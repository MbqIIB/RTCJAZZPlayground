package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptResultDisplayer;
import com.ibm.team.filesystem.cli.core.internal.ScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.ITypedPreferenceRegistry;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDiscardChangeSets;
import com.ibm.team.filesystem.client.rest.parameters.ParmsOutOfSyncInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsRemoveSuspendedChangeSets;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.DiscardResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.SandboxUpdateDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ComponentSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.SyncViewDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkspaceSyncDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.DiscardNotInHistoryException;
import com.ibm.team.scm.common.GapException;
import com.ibm.team.scm.common.NWayConflictUnsupportedException;
import com.ibm.team.scm.common.PatchInProgressException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.osgi.util.NLS;




public class ChangesetDiscardCmd
  extends AbstractSubcommand
{
  public ChangesetDiscardCmd() {}
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId(), null), config);
    
    SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
    List<IScmCommandLineArgument> csSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(CommonOptions.OPT_CHANGESET_SELECTORS), config);
    SubcommandUtil.validateArgument(csSelectors, RepoUtil.ItemType.CHANGESET);
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    List<ParmsWorkspace> wsList = new ArrayList();
    
    if (wsSelector != null) {
      ParmsWorkspace ws = RepoUtil.findWorkspaceAndLogin(wsSelector, client, config);
      ITeamRepository repo = RepoUtil.getSharedRepository(repositoryUrl, true);
      

      RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, csSelectors, repo, config);
      
      wsList.add(ws);
    } else {
      List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, 
        config);
      
      for (ISandboxWorkspace wsInSandbox : wsInSandboxList) {
        String uri = RepoUtil.getRepoUri(config, client, wsInSandbox.getRepositoryId(), 
          Collections.singletonList(wsInSandbox));
        RepoUtil.login(config, client, config.getConnectionInfo(uri));
        wsList.add(new ParmsWorkspace(uri, wsInSandbox.getWorkspaceItemId()));
      }
    }
    

    SyncViewDTO syncView = SubcommandUtil.getSyncView(wsList, false, client, config);
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    if (syncView.getWorkspaces().size() == 0) {
      throw StatusHelper.itemNotFound(Messages.Common_WS_NOT_FOUND);
    }
    
    Object foundCsList = new ArrayList();
    

    Map<ParmsWorkspace, List<ChangeSetSyncDTO>> wsToOutgoingCsMap = findDiscardChangesets(csSelectors, syncView, true, client, config, out);
    Iterator localIterator3;
    ChangeSetSyncDTO changeSetSync;
    for (Iterator localIterator2 = wsToOutgoingCsMap.entrySet().iterator(); localIterator2.hasNext(); 
        localIterator3.hasNext())
    {
      Map.Entry<ParmsWorkspace, List<ChangeSetSyncDTO>> entry = (Map.Entry)localIterator2.next();
      localIterator3 = ((List)entry.getValue()).iterator(); continue;changeSetSync = (ChangeSetSyncDTO)localIterator3.next();
      ((List)foundCsList).add(changeSetSync.getChangeSetItemId());
    }
    
    Map<ParmsWorkspace, List<ChangeSetSyncDTO>> wsToSuspendCsMap = null;
    Iterator localIterator4; if (((List)foundCsList).size() != csSelectors.size()) {
      wsToSuspendCsMap = findDiscardChangesets(csSelectors, syncView, false, client, config, out);
      for (changeSetSync = wsToSuspendCsMap.entrySet().iterator(); changeSetSync.hasNext(); 
          localIterator4.hasNext())
      {
        Object entry = (Map.Entry)changeSetSync.next();
        localIterator4 = ((List)((Map.Entry)entry).getValue()).iterator(); continue;ChangeSetSyncDTO changeSetSync = (ChangeSetSyncDTO)localIterator4.next();
        ((List)foundCsList).add(changeSetSync.getChangeSetItemId());
      }
    }
    


    Object wsToHistoryCsMap = null;
    if (((List)foundCsList).size() != csSelectors.size()) {
      List<IScmCommandLineArgument> csSelectorsInHistory = new ArrayList();
      for (IScmCommandLineArgument csSelector : csSelectors) {
        IUuidAliasRegistry.IUuidAlias csUuidAlias = RepoUtil.lookupUuidAndAlias(csSelector.getItemSelector());
        if (!((List)foundCsList).contains(csUuidAlias.getUuid().getUuidValue())) {
          csSelectorsInHistory.add(csSelector);
        }
      }
      
      wsToHistoryCsMap = findDiscardChangesetsInHistory(csSelectorsInHistory, wsList, client, config, out);
    }
    

    if ((wsToOutgoingCsMap.size() > 0) || ((wsToHistoryCsMap != null) && (((Map)wsToHistoryCsMap).size() > 0))) {
      Map<ParmsWorkspace, List<ChangeSetSyncDTO>> wsToCsMap = new HashMap(wsToOutgoingCsMap);
      if ((wsToHistoryCsMap != null) && (((Map)wsToHistoryCsMap).size() > 0)) {
        for (Object entryHistory : ((Map)wsToHistoryCsMap).entrySet()) {
          List<ChangeSetSyncDTO> csList = null;
          
          for (Map.Entry<ParmsWorkspace, List<ChangeSetSyncDTO>> entry : wsToCsMap.entrySet()) {
            if (getKeyworkspaceItemId.equals(getKeyworkspaceItemId)) {
              csList = (List)entry.getValue();
              break;
            }
          }
          
          if (csList != null) {
            csList.addAll((Collection)((Map.Entry)entryHistory).getValue());
          } else {
            wsToCsMap.put((ParmsWorkspace)((Map.Entry)entryHistory).getKey(), (List)((Map.Entry)entryHistory).getValue());
          }
        }
      }
      
      discardChangesets(wsToCsMap, client, config, 
        Messages.DiscardCmd_CHANGESETS_DISCARDED, out);
    }
    

    if ((wsToSuspendCsMap != null) && (wsToSuspendCsMap.size() > 0)) {
      String header = wsToOutgoingCsMap.size() == 0 ? 
        Messages.DiscardCmd_CHANGESETS_DISCARDED : null;
      discardSuspendedChangesets(wsToSuspendCsMap, client, config, header, out);
    }
    
    out.println(Messages.DiscardCmd_CHANGESETS_SUCCESSFULLY_DISCARDED);
  }
  
  private void printError(IScmCommandLineArgument csSelector, IndentingPrintStream out) throws FileSystemException {
    out.println(Messages.Common_CS_NOT_FOUND_HEADER);
    out.indent().println(csSelector.getItemSelector());
    out.println(Messages.DiscardCmd_HINT);
    throw StatusHelper.itemNotFound(Messages.Common_CS_NOT_FOUND);
  }
  
  private Map<ParmsWorkspace, List<ChangeSetSyncDTO>> findDiscardChangesets(List<IScmCommandLineArgument> csSelectors, SyncViewDTO syncView, boolean inOutgoing, IFilesystemRestClient client, IScmClientConfiguration config, IndentingPrintStream out)
    throws FileSystemException
  {
    Map<ParmsWorkspace, List<ChangeSetSyncDTO>> wsToCsMap = new HashMap();
    Map<String, ParmsWorkspace> wsIdToWs = new HashMap();
    

    for (IScmCommandLineArgument csSelector : csSelectors) {
      IUuidAliasRegistry.IUuidAlias csUuidAlias = RepoUtil.lookupUuidAndAlias(csSelector.getItemSelector());
      if (csUuidAlias == null) {
        printError(csSelector, out);
      }
      
      Iterator localIterator3;
      for (Iterator localIterator2 = syncView.getWorkspaces().iterator(); localIterator2.hasNext(); 
          





          localIterator3.hasNext())
      {
        WorkspaceSyncDTO wsSync = (WorkspaceSyncDTO)localIterator2.next();
        ParmsWorkspace ws = (ParmsWorkspace)wsIdToWs.get(wsSync.getWorkspaceItemId());
        if (ws == null) {
          ws = new ParmsWorkspace(wsSync.getRepositoryUrl(), wsSync.getWorkspaceItemId());
          wsIdToWs.put(wsSync.getWorkspaceItemId(), ws);
        }
        
        localIterator3 = wsSync.getComponents().iterator(); continue;ComponentSyncDTO compSync = (ComponentSyncDTO)localIterator3.next();
        List<ChangeSetSyncDTO> changeSetSyncList = null;
        if (inOutgoing) {
          changeSetSyncList = compSync.getOutgoingChangeSetsAfterBasis();
        } else {
          changeSetSyncList = compSync.getSuspended();
        }
        for (ChangeSetSyncDTO changeSetSync : changeSetSyncList) {
          if ((csUuidAlias != null) && (csUuidAlias.getUuid().getUuidValue().equals(changeSetSync.getChangeSetItemId())) && (
            (csSelector.getRepositorySelector() == null) || 
            (RepoUtil.isRepoUriSame(csSelector.getRepositorySelector(), changeSetSync.getRepositoryUrl(), config)))) {
            List<ChangeSetSyncDTO> csList = (List)wsToCsMap.get(ws);
            if (csList == null) {
              csList = new ArrayList();
              wsToCsMap.put(ws, csList);
            }
            

            changeSetSync.setComponentItemId(compSync.getComponentItemId());
            if (csList.contains(changeSetSync)) break;
            csList.add(changeSetSync);
            
            break;
          }
        }
      }
    }
    


    return wsToCsMap;
  }
  
  private Map<ParmsWorkspace, List<ChangeSetSyncDTO>> findDiscardChangesetsInHistory(List<IScmCommandLineArgument> csSelectors, List<ParmsWorkspace> wsList, IFilesystemRestClient client, IScmClientConfiguration config, IndentingPrintStream out)
    throws FileSystemException
  {
    Map<ParmsWorkspace, List<ChangeSetSyncDTO>> wsToCsMap = new HashMap();
    Map<String, ParmsWorkspace> wsIdToWs = new HashMap();
    
    for (IScmCommandLineArgument csSelector : csSelectors) {
      boolean foundCs = false;
      ParmsWorkspace wsToUse = null;
      ChangeSetSyncDTO changeSetSync = null;
      
      for (ParmsWorkspace ws : wsList) {
        wsToUse = (ParmsWorkspace)wsIdToWs.get(workspaceItemId);
        if (wsToUse == null) {
          wsToUse = ws;
          wsIdToWs.put(workspaceItemId, wsToUse);
        }
        try
        {
          changeSetSync = RepoUtil.findChangeSet(csSelector.getItemSelector(), false, workspaceItemId, 
            "workspace", repositoryUrl, client, config);
        } catch (FileSystemException localFileSystemException) {
          continue;
        }
        foundCs = true;
        break;
      }
      
      if (!foundCs) {
        printError(csSelector, out);
      }
      

      List<ChangeSetSyncDTO> csList = (List)wsToCsMap.get(wsToUse);
      if (csList == null) {
        csList = new ArrayList();
        wsToCsMap.put(wsToUse, csList);
      }
      csList.add(changeSetSync);
    }
    
    return wsToCsMap;
  }
  
  private void discardChangesets(Map<ParmsWorkspace, List<ChangeSetSyncDTO>> wsToCsMap, IFilesystemRestClient client, IScmClientConfiguration config, String header, IndentingPrintStream out) throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    

    for (Map.Entry<ParmsWorkspace, List<ChangeSetSyncDTO>> entry : wsToCsMap.entrySet()) {
      ParmsDiscardChangeSets parmsCs = new ParmsDiscardChangeSets();
      workspace = ((ParmsWorkspace)entry.getKey());
      
      List<ChangeSetSyncDTO> csList = (List)entry.getValue();
      changeSetItemIds = new String[csList.size()];
      int count = 0;
      for (ChangeSetSyncDTO cs : csList) {
        changeSetItemIds[(count++)] = cs.getChangeSetItemId();
      }
      
      preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
      
      pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
      if (cli.hasOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED)) {
        pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
      } else {
        pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
      }
      
      outOfSyncInstructions = new ParmsOutOfSyncInstructions();
      outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
      outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
      
      sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
      sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
      sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
      
      sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
      sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
      
      if (!cli.hasOption(CommonOptions.OPT_RELEASE_LOCK)) {
        autoReleaseLocks = Boolean.valueOf(((ScmClientConfiguration)config).getPersistentPreferences().getReleaseAutoLockForDiscard());
      }
      else {
        autoReleaseLocks = Boolean.valueOf(Boolean.parseBoolean(cli.getOption(CommonOptions.OPT_RELEASE_LOCK).toString()));
      }
      
      try
      {
        result = client.postDiscardChangeSets(parmsCs, null);
      } catch (TeamRepositoryException e) {
        DiscardResultDTO result;
        PatchInProgressException port = (PatchInProgressException)SubcommandUtil.findExceptionByType(PatchInProgressException.class, e);
        if (port != null) {
          throw StatusHelper.portsInProgress(port.getLocalizedMessage());
        }
        
        GapException gap = (GapException)SubcommandUtil.findExceptionByType(GapException.class, e);
        if (gap != null) {
          throw StatusHelper.gap(Messages.DiscardCmd_9 + " " + Messages.DeliverCmd_HINT_ON_GAP);
        }
        
        NWayConflictUnsupportedException nway = (NWayConflictUnsupportedException)SubcommandUtil.findExceptionByType(NWayConflictUnsupportedException.class, e);
        if (nway != null) {
          throw StatusHelper.nWayConflict(Messages.DiscardCmd_10);
        }
        
        DiscardNotInHistoryException notInHist = (DiscardNotInHistoryException)SubcommandUtil.findExceptionByType(DiscardNotInHistoryException.class, e);
        if (notInHist != null) {
          throw StatusHelper.discardNotInHistory(Messages.DiscardCmd_ChangesetNotInHistory);
        }
        
        throw StatusHelper.wrap(Messages.DiscardCmd_11, e, new IndentingPrintStream(config.getContext().stderr()));
      }
      DiscardResultDTO result;
      if (result.isCancelled()) {
        if (result.getOutOfSyncShares().size() > 0) {
          AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
        }
        
        int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
        if (noOfUncheckedInChanges > 0) {
          throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, Integer.valueOf(noOfUncheckedInChanges), CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
        }
      }
      

      if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
        out.println(header);
        printDiscardedChangesets((ParmsWorkspace)entry.getKey(), csList, client, config, out);
      }
      
      if (result.getSandboxUpdateDilemma().getBackedUpToShed().size() > 0) {
        SubcommandUtil.showShedUpdate(Messages.AcceptResultDisplayer_SHED_MESSAGE, out, result.getSandboxUpdateDilemma().getBackedUpToShed());
      }
      
      if (result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) {
        SubcommandUtil.showDeletedContent(result.getSandboxUpdateDilemma().getDeletedContentShareables(), out);
      }
    }
  }
  
  private void printDiscardedChangesets(ParmsWorkspace ws, List<ChangeSetSyncDTO> csList, IFilesystemRestClient client, IScmClientConfiguration config, IndentingPrintStream out) throws FileSystemException
  {
    config.getSubcommandCommandLine();
    
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.setVerbose(true);
    options.enablePrinter(4);
    options.enableFilter(4);
    
    for (ChangeSetSyncDTO cs : csList) {
      options.addFilter(UUID.valueOf(cs.getChangeSetItemId()), 4);
    }
    PendingChangesUtil.printChangeSets(null, csList, null, options, out, client, config);
  }
  
  private void discardSuspendedChangesets(Map<ParmsWorkspace, List<ChangeSetSyncDTO>> wsToCsMap, IFilesystemRestClient client, IScmClientConfiguration config, String header, IndentingPrintStream out) throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    

    for (Map.Entry<ParmsWorkspace, List<ChangeSetSyncDTO>> entry : wsToCsMap.entrySet()) {
      ParmsRemoveSuspendedChangeSets parmsCs = new ParmsRemoveSuspendedChangeSets();
      workspace = ((ParmsWorkspace)entry.getKey());
      
      List<ChangeSetSyncDTO> csList = (List)entry.getValue();
      changeSetItemIds = new String[csList.size()];
      int count = 0;
      for (ChangeSetSyncDTO cs : csList) {
        changeSetItemIds[(count++)] = cs.getChangeSetItemId();
      }
      try
      {
        client.postRemoveSuspendedChangeSets(parmsCs, null);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.DiscardCmd_11, e, new IndentingPrintStream(config.getContext().stderr()));
      }
      

      if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
        if (header != null) {
          out.println(header);
        }
        
        printDiscardedChangesets((ParmsWorkspace)entry.getKey(), csList, client, config, out);
      }
    }
  }
}
