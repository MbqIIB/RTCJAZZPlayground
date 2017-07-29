package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsChangesToRelocate;
import com.ibm.team.filesystem.client.rest.parameters.ParmsRelocateChanges;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourceProperties;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeFolderSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ComponentSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.SyncViewDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkspaceSyncDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.osgi.util.NLS;

public class ChangesetRelocateCmd
  extends AbstractSubcommand
{
  public ChangesetRelocateCmd() {}
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId(), null), config);
    SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
    IScmCommandLineArgument csSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetRelocateCmdOptions.OPT_DST_CHANGESET.getId()), config);
    SubcommandUtil.validateArgument(csSelector, RepoUtil.ItemType.CHANGESET);
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ParmsWorkspace ws = null;
    ITeamRepository repo = null;
    
    if (wsSelector != null) {
      ws = RepoUtil.findWorkspaceAndLogin(wsSelector, client, config);
      repo = RepoUtil.getSharedRepository(repositoryUrl, true);
      

      RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, Collections.singletonList(csSelector), repo, config);
    }
    else {
      repo = RepoUtil.loginUrlArgAncestor(config, client, csSelector);
      ws = RepoUtil.findWorkspaceInSandbox(null, repo.getId(), client, config);
    }
    

    ChangeSetSyncDTO targetCsDTO = RepoUtil.findChangeSet(csSelector.getItemSelector(), false, workspaceItemId, 
      "workspace", repo.getRepositoryURI(), client, config);
    if (!targetCsDTO.isIsActive()) {
      throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ChangesetRelocateCmd_CS_NOT_ACTIVE, csSelector.getItemSelector()));
    }
    

    List<String> selectors = cli.getOptions(ChangesetRelocateCmdOptions.OPT_CHANGES.getId());
    Set<String> absSelectorPaths = new HashSet();
    Map<String, String> selectorToChangeIdMap = new HashMap();
    

    for (String selector : selectors) {
      IUuidAliasRegistry.IUuidAlias uuidAlias = RepoUtil.lookupUuidAndAlias(selector);
      if (uuidAlias == null) {
        ILocation absPath = SubcommandUtil.makeAbsolutePath(config, selector);
        if (!SubcommandUtil.exists(absPath, null)) {
          throw StatusHelper.argSyntax(NLS.bind(Messages.Common_PATH_DOES_NOT_EXIST, selector));
        }
        absSelectorPaths.add(absPath.toOSString());
      }
      else if (!selectorToChangeIdMap.values().contains(uuidAlias.getUuid().getUuidValue())) {
        selectorToChangeIdMap.put(selector, uuidAlias.getUuid().getUuidValue());
      }
    }
    


    Map<String, ResourcePropertiesDTO> resourceProperties = new HashMap(absSelectorPaths.size());
    ResourcesDTO resourcesDTO = null;
    String fullPath; if (absSelectorPaths.size() > 0) {
      ParmsResourceProperties parms = new ParmsResourceProperties(false, (String[])absSelectorPaths.toArray(new String[absSelectorPaths.size()]));
      try {
        resourcesDTO = client.getResourceProperties(parms, null);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.PropertyListCmd_CouldNotFetchProperty, e, new IndentingPrintStream(config.getContext().stderr()));
      }
      for (ResourcePropertiesDTO dto : resourcesDTO.getResourceProperties()) {
        if (dto.getShare() == null)
          throw StatusHelper.argSyntax(NLS.bind(Messages.Common_PATH_NOT_SHARED, dto.getFullPath()));
        if ((!dto.getShare().getContextItemId().equals(workspaceItemId)) || 
          (!dto.getShare().getComponentItemId().equals(targetCsDTO.getComponentItemId()))) {
          throw StatusHelper.argSyntax(Messages.ChangesetRelocateCmd_9);
        }
        fullPath = dto.getFullPath();
        resourceProperties.put(fullPath, dto);
      }
    }
    


    SyncViewDTO syncView = SubcommandUtil.getSyncView(Collections.singletonList(ws), false, client, config);
    

    List<String> activeChangeSets = new ArrayList();
    Object localObject1; for (WorkspaceSyncDTO wsSync : syncView.getWorkspaces()) {
      if (wsSync.getWorkspaceItemId().equals(workspaceItemId)) {
        for (ComponentSyncDTO compSync : wsSync.getComponents()) {
          if (compSync.getComponentItemId().equals(targetCsDTO.getComponentItemId())) {
            for (localObject1 = compSync.getOutgoingChangeSetsAfterBasis().iterator(); ((Iterator)localObject1).hasNext();) { changeSetSync = (ChangeSetSyncDTO)((Iterator)localObject1).next();
              if (changeSetSync.isIsActive()) {
                activeChangeSets.add(changeSetSync.getChangeSetItemId());
              }
            }
          }
        }
      }
    }
    

    ChangeSetSyncDTO[] csDTOList = RepoUtil.findChangeSets(activeChangeSets, true, workspaceItemId, 
      "workspace", repositoryUrl, client, config);
    
    Map<String, List<String>> csToChangesMap = new HashMap();
    ChangeSetSyncDTO changeSetSync = (localObject1 = csDTOList).length; ChangeSyncDTO changeSync; for (ChangeSetSyncDTO localChangeSetSyncDTO1 = 0; localChangeSetSyncDTO1 < changeSetSync; localChangeSetSyncDTO1++) { ChangeSetSyncDTO changeSetSync = localObject1[localChangeSetSyncDTO1];
      Iterator localIterator5; for (Iterator localIterator4 = changeSetSync.getChanges().iterator(); localIterator4.hasNext(); 
          localIterator5.hasNext())
      {
        changeFolderSync = (ChangeFolderSyncDTO)localIterator4.next();
        localIterator5 = changeFolderSync.getChanges().iterator(); continue;changeSync = (ChangeSyncDTO)localIterator5.next();
        List<String> changes = (List)csToChangesMap.get(changeSetSync.getChangeSetItemId());
        if (changes == null) {
          changes = new ArrayList();
          csToChangesMap.put(changeSetSync.getChangeSetItemId(), changes);
        }
        
        changes.add(changeSync.getVersionableItemId());
      }
    }
    


    Map<String, List<String>> csToChangesRelocateMap = new HashMap();
    if (resourcesDTO != null) {
      for (ResourcePropertiesDTO dto : resourcesDTO.getResourceProperties()) {
        if (!findChangeAndAdd(csToChangesMap, csToChangesRelocateMap, dto.getItemId())) {
          throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ChangesetRelocateCmd_7, dto.getFullPath()));
        }
      }
    }
    
    for (Object entry : selectorToChangeIdMap.entrySet()) {
      if (!findChangeAndAdd(csToChangesMap, csToChangesRelocateMap, (String)((Map.Entry)entry).getValue())) {
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ChangesetRelocateCmd_7, ((Map.Entry)entry).getKey()));
      }
    }
    

    ParmsRelocateChanges parmsRelocate = new ParmsRelocateChanges();
    workspace = ws;
    targetChangeSetItemId = targetCsDTO.getChangeSetItemId();
    
    List<ParmsChangesToRelocate> parmsChanges = new ArrayList();
    for (ChangeFolderSyncDTO changeFolderSync = csToChangesRelocateMap.entrySet().iterator(); changeFolderSync.hasNext(); 
        changeSync.hasNext())
    {
      Object entry = (Map.Entry)changeFolderSync.next();
      changeSync = ((List)((Map.Entry)entry).getValue()).iterator(); continue;String change = (String)changeSync.next();
      ParmsChangesToRelocate parmsChange = new ParmsChangesToRelocate();
      changeSetItemId = ((String)((Map.Entry)entry).getKey());
      versionableItemId = change;
      
      parmsChanges.add(parmsChange);
    }
    
    changesToRelocate = ((ParmsChangesToRelocate[])parmsChanges.toArray(new ParmsChangesToRelocate[parmsChanges.size()]));
    
    if (config.isDryRun()) {
      return;
    }
    try
    {
      client.postRelocateChanges(parmsRelocate, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ChangesetRelocateCmd_FAILURE, e, new IndentingPrintStream(config.getContext().stderr()));
    }
    
    config.getContext().stdout().println(Messages.ChangesetRelocateCmd_5);
  }
  





  private boolean findChangeAndAdd(Map<String, List<String>> csToChangesMap, Map<String, List<String>> csToChangesRelocateMap, String changeId)
  {
    boolean found = false;
    
    for (Map.Entry<String, List<String>> entry : csToChangesMap.entrySet()) {
      if (((List)entry.getValue()).contains(changeId)) {
        List<String> changes = (List)csToChangesRelocateMap.get(entry.getKey());
        if (changes == null) {
          changes = new ArrayList();
          csToChangesRelocateMap.put((String)entry.getKey(), changes);
        }
        
        changes.add(changeId);
        found = true;
        break;
      }
    }
    
    return found;
  }
}
