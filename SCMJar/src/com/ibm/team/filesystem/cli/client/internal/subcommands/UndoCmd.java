package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.ITypedPreferenceRegistry;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.SandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsOutOfSyncInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxPaths;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUndoChanges;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUndoCheckedInChanges;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUndoLocalChanges;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUndoLocalChangesRequest;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.BackupInShedDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.UndoCheckedInChangesResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.UndoLocalChangesResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.SandboxUpdateDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.SandboxPathsResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeFolderSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ComponentSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.LocalChangeSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.SyncViewDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.UnresolvedFolderSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkspaceSyncDTO;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;


public class UndoCmd
  extends AbstractSubcommand
{
  public UndoCmd() {}
  
  public static class Change
  {
    String selector;
    ShareableDTO shareable;
    ResourcePropertiesDTO resource;
    
    public Change(String selector, ShareableDTO shareable, ResourcePropertiesDTO resource)
    {
      this.selector = selector;
      this.shareable = shareable;
      this.resource = resource;
    }
    
    public String getSelector() {
      return selector;
    }
    
    public String getSandboxPath() {
      if (shareable != null) {
        return shareable.getSandboxPath();
      }
      return null;
    }
    
    public String getRelativePath() {
      return getRelativePathToComponent(null);
    }
    
    public String getRelativePathToComponent()
    {
      String compName = null;
      if (resource != null) {
        ShareDTO share = resource.getShare();
        compName = share.getComponentName();
      }
      return getRelativePathToComponent(compName);
    }
    
    public String getRelativePathToComponent(String compName) {
      if (shareable != null) {
        List<String> changeSegments = shareable.getRelativePath().getSegments();
        if ((compName != null) && (compName.equalsIgnoreCase((String)changeSegments.get(0)))) {
          changeSegments.remove(0);
        }
        String relPath = StringUtil.createPathString((String[])changeSegments.toArray(new String[changeSegments.size()]));
        return relPath;
      }
      return null;
    }
    
    public String getFullPath() {
      if (shareable != null) {
        IPath path = new Path(shareable.getSandboxPath());
        return path.append(getRelativePath()).toOSString();
      }
      return null;
    }
    
    public String getItemId() {
      if (resource != null) {
        return resource.getItemId();
      }
      
      IUuidAliasRegistry.IUuidAlias alias = RepoUtil.lookupUuidAndAlias(selector);
      if (alias != null) {
        return alias.getUuid().getUuidValue();
      }
      
      return null;
    }
    
    public boolean hasItemId() {
      return getItemId() != null;
    }
    
    public boolean hasResourceInfo() {
      return resource != null;
    }
    
    public boolean hasShareInfo() {
      return shareable != null;
    }
    
    public ResourcePropertiesDTO getResourceProperties() {
      return resource;
    }
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine subargs = config.getSubcommandCommandLine();
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    List<String> changeSelectors = subargs.getOptions(UndoCmdOpts.OPT_CHANGES);
    subargs.hasOption(CommonOptions.OPT_VERBOSE);
    
    List<String> pathSelectors = new ArrayList();
    

    for (String changeSelector : changeSelectors) {
      ILocation changePath = SubcommandUtil.makeAbsolutePath(config, changeSelector);
      pathSelectors.add(changePath.toOSString());
    }
    

    ParmsSandboxPaths parmsPaths = new ParmsSandboxPaths();
    includeNonRegisteredSandboxes = true;
    pathsToResolve = ((String[])pathSelectors.toArray(new String[pathSelectors.size()]));
    
    SandboxPathsResultDTO pathsResult = null;
    try {
      pathsResult = client.getSandboxPaths(parmsPaths, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.Common_UNABLE_TO_GET_SANDBOX_PATHS, e, new IndentingPrintStream(config.getContext().stderr()), null);
    }
    

    List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, config);
    Map<String, ISandboxWorkspace> wsIdtoWsInSandboxmap = new HashMap();
    
    for (ISandboxWorkspace wsInSandbox : wsInSandboxList) {
      wsIdtoWsInSandboxmap.put(wsInSandbox.getWorkspaceItemId(), wsInSandbox);
    }
    

    List<Change> changesToFindList = new ArrayList();
    
    int count = 0;
    ResourcePropertiesDTO resource; for (ShareableDTO shareable : pathsResult.getPaths()) {
      resource = null;
      
      if ((shareable.getSandboxPath() != null) && (shareable.getSandboxPath().length() > 0) && 
        (shareable.getRelativePath() != null) && (shareable.getRelativePath().getSegments().size() > 0)) {
        try {
          resource = RepoUtil.getResourceProperties((String)pathSelectors.get(count), client, config);
          
          ShareDTO share = resource.getShare();
          if (!wsIdtoWsInSandboxmap.keySet().contains(share.getContextItemId())) {
            wsIdtoWsInSandboxmap.put(
              share.getContextItemId(), 
              new SandboxWorkspace(share.getContextItemId(), share
              .getContextName(), share.getRepositoryId()));
          }
        }
        catch (Exception localException) {}
        
        changesToFindList.add(new Change((String)changeSelectors.get(count), shareable, resource));
      } else {
        changesToFindList.add(new Change((String)changeSelectors.get(count), null, null));
      }
      
      count++;
    }
    

    List<ParmsWorkspace> wsList = new ArrayList(wsIdtoWsInSandboxmap.values().size());
    String uri; for (ISandboxWorkspace wsInSandbox : wsIdtoWsInSandboxmap.values()) {
      uri = RepoUtil.getRepoUri(config, client, wsInSandbox.getRepositoryId(), 
        Collections.singletonList(wsInSandbox));
      RepoUtil.login(config, client, config.getConnectionInfo(uri));
      wsList.add(new ParmsWorkspace(uri, wsInSandbox.getWorkspaceItemId()));
    }
    

    if (SubcommandUtil.shouldRefreshFileSystem(config)) {
      Object sandboxToPaths = new HashMap();
      for (Change change : changesToFindList) {
        if (change.hasShareInfo()) {
          List<ILocation> pathLocs = (List)((Map)sandboxToPaths).get(change.getSandboxPath());
          if (pathLocs == null) {
            pathLocs = new ArrayList();
            ((Map)sandboxToPaths).put(change.getSandboxPath(), pathLocs);
          }
          
          ILocation pathLoc = new PathLocation(change.getSandboxPath()).append(change.getRelativePath());
          pathLocs.add(pathLoc);
        }
      }
      
      for (Map.Entry<String, List<ILocation>> entry : ((Map)sandboxToPaths).entrySet()) {
        ILocation sandboxLoc = new PathLocation((String)entry.getKey());
        SubcommandUtil.refreshPaths(sandboxLoc, (List)entry.getValue(), client, config);
      }
    }
    

    SyncViewDTO syncView = SubcommandUtil.getSyncView(wsList, false, client, config);
    if (syncView.getWorkspaces().size() == 0) {
      throw StatusHelper.itemNotFound(Messages.Common_WS_NOT_FOUND);
    }
    syncView = SubcommandUtil.getSyncView(wsList, true, client, config);
    

    List<Change> localChanges = new ArrayList(changesToFindList);
    
    List<ParmsUndoLocalChangesRequest> undoLocalChangesRequests = generateUndoLocalChangeRequests(
      changesToFindList, syncView, config);
    

    localChanges.removeAll(changesToFindList);
    
    Map<ParmsWorkspace, List<ParmsUndoChanges>> wsToUndoChanges = null;
    if (changesToFindList.size() > 0) {
      wsToUndoChanges = generateUndoChanges(changesToFindList, syncView, config);
    }
    
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stdout());
    if (changesToFindList.size() > 0) {
      err.println(Messages.UndoCmd_3);
      printChanges(changesToFindList, err.indent());
      throw StatusHelper.itemNotFound(Messages.UndoCmd_6);
    }
    
    List<ShareableDTO> deletedContentShareables = new ArrayList();
    
    if (undoLocalChangesRequests.size() > 0) {
      undoLocalChangesRequests(undoLocalChangesRequests, deletedContentShareables, client, config);
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    if ((wsToUndoChanges != null) && (wsToUndoChanges.size() > 0)) {
      boolean printLocalChanges = true;
      try {
        undoChanges(wsToUndoChanges, deletedContentShareables, client, config, out);
        printLocalChanges = false;
      } finally {
        if (printLocalChanges) {
          out.println(Messages.UndoCmd_CHANGES_UNDONE_HEADER);
          printChanges(localChanges, out.indent());
        }
        if (deletedContentShareables.size() > 0) {
          SubcommandUtil.showDeletedContent(deletedContentShareables, out);
        }
      }
    }
    
    out.println(Messages.UndoCmd_CHANGES_SUCCESSFULLY_UNDONE);
  }
  
  private void printChanges(List<Change> changes, IndentingPrintStream out) {
    for (Change change : changes) {
      out.println(selector);
    }
  }
  
  private List<ParmsUndoLocalChangesRequest> generateUndoLocalChangeRequests(List<Change> changesToFind, SyncViewDTO syncView, IScmClientConfiguration config)
  {
    List<ParmsUndoLocalChangesRequest> undoLocalChangesRequests = new ArrayList();
    Iterator<Change> itChange = changesToFind.iterator();
    label637:
    while (itChange.hasNext()) {
      Change change = (Change)itChange.next();
      ShareDTO share = change.hasResourceInfo() ? change.getResourceProperties().getShare() : null;
      
      Iterator localIterator2;
      for (Iterator localIterator1 = syncView.getWorkspaces().iterator(); localIterator1.hasNext(); 
          
          localIterator2.hasNext())
      {
        WorkspaceSyncDTO wsSync = (WorkspaceSyncDTO)localIterator1.next();
        if ((share != null) && (!share.getContextItemId().equals(wsSync.getWorkspaceItemId()))) break label637;
        localIterator2 = wsSync.getComponents().iterator(); continue;ComponentSyncDTO compSync = (ComponentSyncDTO)localIterator2.next();
        if ((share == null) || (share.getComponentItemId().equals(compSync.getComponentItemId()))) {
          Iterator localIterator4;
          for (Iterator localIterator3 = compSync.getUnresolved().iterator(); localIterator3.hasNext(); 
              localIterator4.hasNext())
          {
            UnresolvedFolderSyncDTO unresolvedFolderSync = (UnresolvedFolderSyncDTO)localIterator3.next();
            localIterator4 = unresolvedFolderSync.getLocalChanges().iterator(); continue;LocalChangeSyncDTO localChange = (LocalChangeSyncDTO)localIterator4.next();
            if (((!change.hasShareInfo()) || (localChange.getSandboxPath().equals(change.getSandboxPath()))) && (
              ((change.hasItemId()) && (localChange.getTargetVersionableItemId().equals(change.getItemId()))) || (
              (!change.hasResourceInfo()) && (localChange.getPath().equals(change.getRelativePath()))))) {
              boolean foundChange = false;
              for (ParmsUndoLocalChangesRequest localCR : undoLocalChangesRequests) {
                if ((workspace.workspaceItemId.equals(wsSync.getWorkspaceItemId())) && 
                  (componentItemId.equals(compSync.getComponentItemId())) && 
                  (sandboxPath.equals(localChange.getSandboxPath())))
                {
                  List<String> versionableIds = new ArrayList(versionableItemIds.length + 1);
                  for (String verId : versionableItemIds) {
                    versionableIds.add(verId);
                  }
                  versionableIds.add(localChange.getTargetVersionableItemId());
                  versionableItemIds = ((String[])versionableIds.toArray(new String[versionableIds.size()]));
                  
                  foundChange = true;
                  break;
                }
              }
              
              if (!foundChange) {
                ParmsUndoLocalChangesRequest localChangeRequest = new ParmsUndoLocalChangesRequest();
                workspace = new ParmsWorkspace(wsSync.getRepositoryUrl(), wsSync.getWorkspaceItemId());
                componentItemId = compSync.getComponentItemId();
                sandboxPath = localChange.getSandboxPath();
                versionableItemIds = new String[1];
                versionableItemIds[0] = localChange.getTargetVersionableItemId();
                undoLocalChangesRequests.add(localChangeRequest);
              }
              
              itChange.remove();
              break;
            }
          }
        }
      }
    }
    




    return undoLocalChangesRequests;
  }
  
  private Map<ParmsWorkspace, List<ParmsUndoChanges>> generateUndoChanges(List<Change> changesToFind, SyncViewDTO syncView, IScmClientConfiguration config)
  {
    Map<ParmsWorkspace, List<ParmsUndoChanges>> wsToUndoChanges = new HashMap();
    Map<String, ParmsWorkspace> wsIdToWs = new HashMap();
    Iterator<Change> itChange = changesToFind.iterator();
    label707:
    while (itChange.hasNext()) {
      Change change = (Change)itChange.next();
      ShareDTO share = change.hasResourceInfo() ? change.getResourceProperties().getShare() : null;
      
      Iterator localIterator2;
      for (Iterator localIterator1 = syncView.getWorkspaces().iterator(); localIterator1.hasNext(); 
          






          localIterator2.hasNext())
      {
        WorkspaceSyncDTO wsSync = (WorkspaceSyncDTO)localIterator1.next();
        if ((share != null) && (!share.getContextItemId().equals(wsSync.getWorkspaceItemId()))) break label707;
        ParmsWorkspace ws = (ParmsWorkspace)wsIdToWs.get(wsSync.getWorkspaceItemId());
        if (ws == null) {
          ws = new ParmsWorkspace(wsSync.getRepositoryUrl(), wsSync.getWorkspaceItemId());
          wsIdToWs.put(workspaceItemId, ws);
        }
        
        localIterator2 = wsSync.getComponents().iterator(); continue;ComponentSyncDTO compSync = (ComponentSyncDTO)localIterator2.next();
        if ((share == null) || (share.getComponentItemId().equals(compSync.getComponentItemId())))
        {
          for (ChangeSetSyncDTO changeSetSync : compSync.getOutgoingChangeSetsAfterBasis()) {
            if (changeSetSync.isIsActive())
            {
              Iterator localIterator5;
              
              for (Iterator localIterator4 = changeSetSync.getChanges().iterator(); localIterator4.hasNext(); 
                  localIterator5.hasNext())
              {
                ChangeFolderSyncDTO changeFolderSync = (ChangeFolderSyncDTO)localIterator4.next();
                localIterator5 = changeFolderSync.getChanges().iterator(); continue;ChangeSyncDTO changeSync = (ChangeSyncDTO)localIterator5.next();
                if (((change.hasItemId()) && (changeSync.getVersionableItemId().equals(change.getItemId()))) || (
                  (!change.hasResourceInfo()) && 
                  (changeSync.getPathHint().equals(change.getRelativePathToComponent(compSync.getComponentName()))))) {
                  boolean foundChange = false;
                  List<ParmsUndoChanges> undoChanges = (List)wsToUndoChanges.get(ws);
                  if (undoChanges != null) {
                    for (ParmsUndoChanges changes : undoChanges) {
                      if (changeSetItemId.equals(changeSetSync.getChangeSetItemId())) {
                        List<String> versionableIds = new ArrayList(versionableItemIds.length);
                        for (String verId : versionableItemIds) {
                          versionableIds.add(verId);
                        }
                        versionableIds.add(changeSync.getVersionableItemId());
                        versionableItemIds = ((String[])versionableIds.toArray(new String[versionableIds.size()]));
                        
                        foundChange = true;
                        break;
                      }
                    }
                  }
                  
                  if (!foundChange) {
                    ParmsUndoChanges changes = new ParmsUndoChanges();
                    changeSetItemId = changeSetSync.getChangeSetItemId();
                    versionableItemIds = new String[1];
                    versionableItemIds[0] = changeSync.getVersionableItemId();
                    
                    if (undoChanges == null) {
                      undoChanges = new ArrayList();
                      wsToUndoChanges.put(ws, undoChanges);
                    }
                    undoChanges.add(changes);
                  }
                  
                  itChange.remove();
                  break;
                }
              }
            }
          }
        }
      }
    }
    


    return wsToUndoChanges;
  }
  
  private void undoLocalChangesRequests(List<ParmsUndoLocalChangesRequest> undoLocalChangesRequests, List<ShareableDTO> deletedContentShareables, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    ParmsUndoLocalChanges parmsLocalChanges = new ParmsUndoLocalChanges();
    undoRequests = ((ParmsUndoLocalChangesRequest[])undoLocalChangesRequests.toArray(new ParmsUndoLocalChangesRequest[undoLocalChangesRequests.size()]));
    
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = false;
    
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
    
    outOfSyncInstructions = new ParmsOutOfSyncInstructions();
    outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
    outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
    
    if (!cli.hasOption(CommonOptions.OPT_RELEASE_LOCK)) {
      autoReleaseLocks = Boolean.valueOf(((ScmClientConfiguration)config).getPersistentPreferences().getReleaseLockForUndo());
    }
    else {
      autoReleaseLocks = Boolean.valueOf(Boolean.parseBoolean(cli.getOption(CommonOptions.OPT_RELEASE_LOCK).toString()));
    }
    
    UndoLocalChangesResultDTO result = null;
    try {
      result = client.postUndoLocalChanges(parmsLocalChanges, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.UndoCmd_6, e, new IndentingPrintStream(config.getContext().stderr()));
    }
    
    if ((result.isCancelled()) && 
      (result.getOutOfSyncShares().size() > 0)) {
      AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
    }
    

    if (result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) {
      deletedContentShareables.addAll(result.getSandboxUpdateDilemma().getDeletedContentShareables());
    }
  }
  
  private void undoChanges(Map<ParmsWorkspace, List<ParmsUndoChanges>> wsToUndoChanges, List<ShareableDTO> deletedContentShareables, IFilesystemRestClient client, IScmClientConfiguration config, IndentingPrintStream out)
    throws FileSystemException
  {
    List<BackupInShedDTO> backupInShedList = new ArrayList();
    
    for (Map.Entry<ParmsWorkspace, List<ParmsUndoChanges>> entry : wsToUndoChanges.entrySet()) {
      ParmsUndoCheckedInChanges parmsUnCheckedInChanges = new ParmsUndoCheckedInChanges();
      workspace = ((ParmsWorkspace)entry.getKey());
      changesToUndo = ((ParmsUndoChanges[])((List)entry.getValue()).toArray(new ParmsUndoChanges[((List)entry.getValue()).size()]));
      
      sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
      sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
      sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
      
      sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
      sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
      
      outOfSyncInstructions = new ParmsOutOfSyncInstructions();
      outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
      outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
      
      UndoCheckedInChangesResultDTO result = null;
      try {
        result = client.postUndoCheckedInChanges(parmsUnCheckedInChanges, null);
      } catch (TeamRepositoryException e) {
        if (backupInShedList.size() > 0) {
          SubcommandUtil.showShedUpdate(Messages.AcceptResultDisplayer_SHED_MESSAGE, out, backupInShedList);
        }
        
        throw StatusHelper.wrap(Messages.UndoCmd_6, e, new IndentingPrintStream(config.getContext().stderr()));
      }
      
      if ((result.isCancelled()) && 
        (result.getOutOfSyncShares().size() > 0)) {
        AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
      }
      

      if (result.getSandboxUpdateDilemma().getBackedUpToShed().size() > 0) {
        backupInShedList.addAll(result.getSandboxUpdateDilemma().getBackedUpToShed());
      }
      
      if (result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) {
        deletedContentShareables.addAll(result.getSandboxUpdateDilemma().getDeletedContentShareables());
      }
    }
    
    if (backupInShedList.size() > 0) {
      SubcommandUtil.showShedUpdate(Messages.AcceptResultDisplayer_SHED_MESSAGE, out, backupInShedList);
    }
  }
}
