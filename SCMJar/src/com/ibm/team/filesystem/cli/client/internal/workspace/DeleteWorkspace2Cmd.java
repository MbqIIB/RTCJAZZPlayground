package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.client.FileSystemException;








public class DeleteWorkspace2Cmd
  extends DeleteWorkspaceCmd
{
  public DeleteWorkspace2Cmd()
  {
    peerWkspRequired = false;
  }
  
  public void run() throws FileSystemException {
    deleteWorkspace(true, false, config, Messages.DeleteWorkspaceCmd_SUCCESS, Messages.DeleteWorkspaceCmd_FAILURE);
  }
  
  public String getItemHelp() {
    return Messages.DeleteWorkspaceCmdOptions_WS2_HELP;
  }
}
