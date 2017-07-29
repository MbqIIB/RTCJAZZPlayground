package com.ibm.team.filesystem.cli.client.internal.flowcommands;

import com.ibm.team.filesystem.cli.client.internal.subcommands.ResolveCmd.LocalConflictToResolve;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.patch.CurrentPatchDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.VersionableChangeDTO;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.IPath;

public abstract interface IConflictHandler
{
  public abstract void configureVerbose(boolean paramBoolean);
  
  public abstract void configureQuiet(boolean paramBoolean);
  
  public abstract void handleConflicts(IPath paramIPath, ParmsWorkspace paramParmsWorkspace, IFilesystemRestClient paramIFilesystemRestClient, IScmClientConfiguration paramIScmClientConfiguration)
    throws FileSystemException;
  
  public abstract void handleLocalConflicts(IPath paramIPath, Map<ParmsWorkspace, List<ResolveCmd.LocalConflictToResolve>> paramMap, IFilesystemRestClient paramIFilesystemRestClient, IScmClientConfiguration paramIScmClientConfiguration)
    throws FileSystemException;
  
  public abstract void handleConflicts(IPath paramIPath, ParmsWorkspace paramParmsWorkspace, CurrentPatchDTO paramCurrentPatchDTO, IFilesystemRestClient paramIFilesystemRestClient, IScmClientConfiguration paramIScmClientConfiguration)
    throws FileSystemException;
  
  public abstract void handleConflicts(IPath paramIPath, ParmsWorkspace paramParmsWorkspace, CurrentPatchDTO paramCurrentPatchDTO, List<VersionableChangeDTO> paramList, IFilesystemRestClient paramIFilesystemRestClient, IScmClientConfiguration paramIScmClientConfiguration)
    throws FileSystemException;
}
