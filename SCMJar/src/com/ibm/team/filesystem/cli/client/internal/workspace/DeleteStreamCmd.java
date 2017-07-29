package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.client.FileSystemException;








public class DeleteStreamCmd
  extends DeleteWorkspaceCmd
{
  public DeleteStreamCmd()
  {
    peerWkspRequired = true;
  }
  
  public void run() throws FileSystemException {
    deleteWorkspace(false, true, config, Messages.DeleteStreamCmd_SUCCESS, Messages.DeleteStreamCmd_FAILURE);
  }
  
  public String getItemHelp() {
    return Messages.DeleteStreamCmdOptions_STREAM_HELP;
  }
  
  public String getItemName() {
    return "stream";
  }
}
