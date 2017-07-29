package com.ibm.team.filesystem.cli.client.internal.lock;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.FileSystemStatusException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLockDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUnlock;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUnlockResources;
import com.ibm.team.filesystem.client.rest.parameters.ParmsVersionable;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.locks.LockEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.locks.UnlockResourcesResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.ITeamRepositoryService;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.LockOperationFailureException;
import com.ibm.team.scm.common.internal.rest.IScmRestService;
import com.ibm.team.scm.common.internal.rest.dto.VersionableDTO;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.core.runtime.IStatus;




public class LockReleaseCmd
  extends LockAcquireCmd
  implements IOptionSource
{
  private static final NamedOptionDefinition OPT_FORCE = new NamedOptionDefinition("f", "force", 0);
  
  protected void addNamedOptions(Options options)
    throws ConflictingOptionException
  {
    super.addNamedOptions(options);
    options.addOption(CommonOptions.OPT_WIDE, CommonOptions.OPT_WIDE_HELP);
    options.addOption(CommonOptions.OPT_VERBOSE, CommonOptions.OPT_VERBOSE_HELP);
    options.addOption(OPT_FORCE, Messages.LockReleaseCmd_OPT_FORCE_HELP);
  }
  
  public LockReleaseCmd()
  {
    super(Messages.LockReleaseCmd_OPT_FILES_TO_UNLOCK_HELP, Messages.LockReleaseCmd_UNLOCKING_FOLDER_PATH_IS_UNSUPPORTED);
  }
  

  protected void lock(ParmsWorkspace ws, WorkspaceComponentDTO comp, boolean includeComponentHierarchy, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine cli)
    throws CLIFileSystemClientException, FileSystemException
  {
    try
    {
      ParmsUnlock lock = new ParmsUnlock();
      workspace = ws;
      componentItemId = comp.getItemId();
      unlockComponentHierarchy = includeComponentHierarchy;
      ParmsUnlockResources parms = new ParmsUnlockResources();
      locks = new ParmsUnlock[] { lock };
      client.postUnlockResources(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(null, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    
    config.getContext().stdout().println(Messages.LockReleaseCmd_LOCKS_SUCCESSFULLY_RELEASED);
  }
  
  protected void lock(ParmsWorkspace ws, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine cli)
    throws CLIFileSystemClientException, FileSystemException
  {
    try
    {
      ParmsUnlock lock = new ParmsUnlock();
      workspace = ws;
      ParmsUnlockResources parms = new ParmsUnlockResources();
      locks = new ParmsUnlock[] { lock };
      client.postUnlockResources(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(null, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    
    config.getContext().stdout().println(Messages.LockReleaseCmd_LOCKS_SUCCESSFULLY_RELEASED);
  }
  
  protected void lock(Map<ParmsWorkspace, List<LockAcquireCmd.LockItem>> streamToLockItems, Map<ParmsWorkspace, ParmsWorkspace> streamToWorkspace, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine cli)
    throws FileSystemException
  {
    Map<String, ITeamRepository> repoIdToObject = new HashMap();
    Map<String, String> repoUriToId = new HashMap();
    

    for (ParmsWorkspace ws : streamToLockItems.keySet()) {
      String repoUri = (String)repoUriToId.get(repositoryUrl);
      if (repoUri == null) {
        ITeamRepository repo = TeamPlatform.getTeamRepositoryService().getTeamRepository(repositoryUrl, 
          0);
        repoUriToId.put(repositoryUrl, repo.getId().getUuidValue());
        repoIdToObject.put(repo.getId().getUuidValue(), repo);
      }
    }
    
    for (Map.Entry<ParmsWorkspace, List<LockAcquireCmd.LockItem>> entry : streamToLockItems.entrySet()) {
      ParmsWorkspace ws = (ParmsWorkspace)entry.getKey();
      
      List<ParmsUnlock> locks = new ArrayList();
      for (LockAcquireCmd.LockItem lockItem : (List)entry.getValue()) {
        ParmsUnlock lock = new ParmsUnlock();
        workspace = new ParmsWorkspace();
        workspace.repositoryUrl = repositoryUrl;
        workspace.workspaceItemId = workspaceItemId;
        componentItemId = lockItem.getComponent();
        versionable = new ParmsVersionable();
        versionable.versionableItemType = lockItem.getVersionableType();
        versionable.itemId = lockItem.getVersionable();
        
        for (Map.Entry<ParmsWorkspace, ParmsWorkspace> sourcews : streamToWorkspace.entrySet())
        {
          if (getKeyworkspaceItemId.equals(workspaceItemId)) {
            sourceWorkspace = ((ParmsWorkspace)sourcews.getValue());
          }
        }
        
        locks.add(lock);
      }
      
      ParmsUnlockResources parms = new ParmsUnlockResources();
      locks = ((ParmsUnlock[])locks.toArray(new ParmsUnlock[locks.size()]));
      lockDilemmaHandler = new ParmsLockDilemmaHandler();
      lockDilemmaHandler.currentUserDoesntOwnLock = (cli.hasOption(OPT_FORCE) ? "continue" : "no");
      
      UnlockResourcesResultDTO result = null;
      try {
        result = client.postUnlockResources(parms, null);
      } catch (FileSystemStatusException e) {
        config.getContext().stderr().print(e.getStatus().getMessage());
        throw StatusHelper.inappropriateArgument(Messages.LockReleaseCmd_FAILURE);
      } catch (LockOperationFailureException e) {
        config.getContext().stderr().print(e.getLocalizedMessage());
        throw StatusHelper.inappropriateArgument(Messages.LockReleaseCmd_FAILURE);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.LockReleaseCmd_FAILURE, e, 
          new IndentingPrintStream(config.getContext().stderr()), null);
      }
      
      if (result.isCancelled()) {
        JSONArray jEntries = new JSONArray();
        

        List<LockEntryDTO> currentUserDoesntOwnLocks = result.getCurrentUserDoesntOwnLocks();
        for (LockEntryDTO lockEntry : currentUserDoesntOwnLocks) {
          ITeamRepository repo = (ITeamRepository)repoIdToObject.get(lockEntry.getRepositoryItemId());
          if (repo != null)
          {



            JSONObject jEntry = jsonizeEntry(repo, lockEntry, config);
            jEntries.add(jEntry);
          }
        }
        boolean verbose = cli.hasOption(CommonOptions.OPT_VERBOSE);
        boolean wide = cli.hasOption(CommonOptions.OPT_WIDE);
        int width = SubcommandUtil.getTerminalWidth(config);
        
        if (jEntries.size() > 0) {
          printEntries(jEntries, verbose, wide, width, config);
        }
      }
    }
    config.getContext().stdout().println(Messages.LockReleaseCmd_LOCKS_SUCCESSFULLY_RELEASED);
  }
  
  private JSONObject jsonizeEntry(ITeamRepository repo, LockEntryDTO lockEntry, IScmClientConfiguration config) throws FileSystemException
  {
    IScmRestService scmService = (IScmRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRestService.class);
    JSONObject jEntry = new JSONObject();
    



    VersionableDTO verDTO = getVersionableById(scmService, lockEntry.getWorkspaceItemId(), 
      lockEntry.getComponentItemId(), lockEntry.getVersionableItemId(), config);
    
    String verPath = Messages.LockListCmd_Unknown;
    if (verDTO != null) {
      verPath = verDTO.getPath();
    }
    
    JSONObject jVer = JSONPrintUtil.jsonize(verPath, lockEntry.getVersionableItemId(), repo.getRepositoryURI());
    jEntry.put("versionable", jVer);
    
    return jEntry;
  }
  
  private void printEntries(JSONArray jEntries, boolean verbose, boolean wide, int width, IScmClientConfiguration config) throws FileSystemException
  {
    JSONObject jNotOwnerLocks = new JSONObject();
    jNotOwnerLocks.put("locks-not-owned", jEntries);
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jNotOwnerLocks);
    } else {
      for (Object obj : jEntries) {
        JSONObject entry = (JSONObject)obj;
        
        JSONObject ver = (JSONObject)entry.get("versionable");
        config.getContext().stdout().println(AliasUtil.selector((String)ver.get("name"), 
          UUID.valueOf((String)ver.get("uuid")), (String)ver.get("url"), 
          RepoUtil.ItemType.VERSIONABLE));
      }
    }
    
    throw StatusHelper.inappropriateArgument(Messages.LockReleaseCmd_SOME_FILES_COULD_NOT_BE_UNLOCKED);
  }
}
