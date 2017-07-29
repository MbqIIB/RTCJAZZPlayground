package com.ibm.team.filesystem.cli.client.internal.flowcommands.conflicthandlers;

import com.ibm.team.filesystem.cli.client.internal.flowcommands.IConflictHandler;
import com.ibm.team.filesystem.cli.client.internal.subcommands.ResolveCmd.LocalConflictToResolve;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.PortsUtil;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.patch.ChangeDetailDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.ContentChangeDetailDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.CurrentPatchDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.VersionableChangeDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeFolderSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ComponentSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ConflictSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.SyncViewDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.UnresolvedFolderSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkspaceSyncDTO;
import com.ibm.team.scm.common.dto.IUpdateReport;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.core.runtime.IPath;













public abstract class AbstractConflictHandler
  implements IConflictHandler
{
  private boolean quiet = false;
  

  private boolean verbose = false;
  private IUpdateReport report;
  
  public AbstractConflictHandler() {}
  
  public void configureQuiet(boolean quiet) {
    this.quiet = quiet;
  }
  
  public void configureVerbose(boolean verbose) {
    this.verbose = verbose;
  }
  
  protected final boolean isQuiet()
  {
    return quiet;
  }
  
  protected final boolean isVerbose() {
    return verbose;
  }
  
  protected final IUpdateReport getReport() {
    return report;
  }
  
  public void handleConflicts(IPath cfaRoot, ParmsWorkspace ws, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    SyncViewDTO syncView = SubcommandUtil.getSyncView(Collections.singletonList(ws), false, client, config);
    
    for (WorkspaceSyncDTO wsSync : syncView.getWorkspaces()) {
      if (wsSync.getWorkspaceItemId().equals(workspaceItemId)) { Iterator localIterator3;
        for (Iterator localIterator2 = wsSync.getComponents().iterator(); localIterator2.hasNext(); 
            localIterator3.hasNext())
        {
          ComponentSyncDTO compSync = (ComponentSyncDTO)localIterator2.next();
          localIterator3 = compSync.getUnresolved().iterator(); continue;UnresolvedFolderSyncDTO unresolvedSync = (UnresolvedFolderSyncDTO)localIterator3.next();
          for (ConflictSyncDTO conflictSync : unresolvedSync.getConflicts()) {
            handleConflict(cfaRoot, ws, compSync, conflictSync, client, config);
          }
        }
      }
    }
  }
  



  public void handleLocalConflicts(IPath cfaRoot, Map<ParmsWorkspace, List<ResolveCmd.LocalConflictToResolve>> conflictsToResolve, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    for (Map.Entry<ParmsWorkspace, List<ResolveCmd.LocalConflictToResolve>> entry : conflictsToResolve.entrySet()) {
      handleLocalConflict(cfaRoot, entry, client, config);
    }
  }
  
  protected void handleConflict(IPath cfaRoot, ParmsWorkspace ws, ComponentSyncDTO compSync, ConflictSyncDTO conflictSync, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    writeConflict(cfaRoot, ws, compSync, conflictSync, client, config);
  }
  



  protected void handleLocalConflict(IPath cfaRoot, Map.Entry<ParmsWorkspace, List<ResolveCmd.LocalConflictToResolve>> entry, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    writeLocalConflict(cfaRoot, entry, client, config);
  }
  

  public void handleConflicts(IPath cfaRoot, ParmsWorkspace ws, CurrentPatchDTO currentPort, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    handleConflicts(cfaRoot, ws, currentPort, currentPort.getChanges(), client, config);
  }
  


  public void handleConflicts(IPath cfaRoot, ParmsWorkspace ws, CurrentPatchDTO currentPort, List<VersionableChangeDTO> changes, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ChangeSetSyncDTO changeSetSync = PortsUtil.getChangeSet(repositoryUrl, currentPort.getSource(), true, client, config);
    label240:
    for (VersionableChangeDTO changeDTO : changes) {
      if (changeDTO.getVersionableType().equals("file")) { Iterator localIterator3;
        for (Iterator localIterator2 = changeDTO.getChangeDetails().iterator(); localIterator2.hasNext(); 
            

            localIterator3.hasNext())
        {
          ChangeDetailDTO detailDTO = (ChangeDetailDTO)localIterator2.next();
          if ((!(detailDTO instanceof ContentChangeDetailDTO)) || 
            (!detailDTO.getKind().equals("conflict")) || (detailDTO.isResolved())) break label240;
          localIterator3 = changeSetSync.getChanges().iterator(); continue;ChangeFolderSyncDTO changeFolderSync = (ChangeFolderSyncDTO)localIterator3.next();
          for (ChangeSyncDTO changeSync : changeFolderSync.getChanges()) {
            if (changeSync.getVersionableItemId().equals(changeDTO.getVersionableItemId())) {
              handleConflict(cfaRoot, ws, changeSetSync.getComponentItemId(), changeDTO, changeSync, client, config);
            }
          }
        }
      }
    }
  }
  



  protected void handleConflict(IPath cfaRoot, ParmsWorkspace ws, String componentId, VersionableChangeDTO changeDTO, ChangeSyncDTO changeSync, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    writeConflict(cfaRoot, ws, componentId, changeDTO, changeSync, client, config);
  }
  
  protected abstract void writeConflict(IPath paramIPath, ParmsWorkspace paramParmsWorkspace, ComponentSyncDTO paramComponentSyncDTO, ConflictSyncDTO paramConflictSyncDTO, IFilesystemRestClient paramIFilesystemRestClient, IScmClientConfiguration paramIScmClientConfiguration)
    throws FileSystemException;
  
  protected abstract void writeConflict(IPath paramIPath, ParmsWorkspace paramParmsWorkspace, String paramString, VersionableChangeDTO paramVersionableChangeDTO, ChangeSyncDTO paramChangeSyncDTO, IFilesystemRestClient paramIFilesystemRestClient, IScmClientConfiguration paramIScmClientConfiguration)
    throws FileSystemException;
  
  protected abstract void writeLocalConflict(IPath paramIPath, Map.Entry<ParmsWorkspace, List<ResolveCmd.LocalConflictToResolve>> paramEntry, IFilesystemRestClient paramIFilesystemRestClient, IScmClientConfiguration paramIScmClientConfiguration)
    throws FileSystemException;
}
