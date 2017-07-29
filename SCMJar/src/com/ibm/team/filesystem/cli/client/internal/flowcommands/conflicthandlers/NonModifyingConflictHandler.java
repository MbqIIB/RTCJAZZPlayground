package com.ibm.team.filesystem.cli.client.internal.flowcommands.conflicthandlers;

import com.ibm.team.filesystem.cli.client.internal.subcommands.ResolveCmd.LocalConflictToResolve;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.patch.VersionableChangeDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ComponentSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ConflictSyncDTO;
import java.util.List;
import java.util.Map.Entry;
import org.eclipse.core.runtime.IPath;

public class NonModifyingConflictHandler
  extends AbstractConflictHandler
{
  public NonModifyingConflictHandler() {}
  
  protected void writeConflict(IPath cfaRoot, ParmsWorkspace ws, ComponentSyncDTO compSync, ConflictSyncDTO conflictSync, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {}
  
  protected void writeLocalConflict(IPath cfaRoot, Map.Entry<ParmsWorkspace, List<ResolveCmd.LocalConflictToResolve>> entry, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {}
  
  protected void writeConflict(IPath cfaRoot, ParmsWorkspace ws, String componentId, VersionableChangeDTO changeDTO, ChangeSyncDTO changeSync, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {}
}
