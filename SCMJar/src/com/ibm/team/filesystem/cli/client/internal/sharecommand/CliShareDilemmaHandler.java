package com.ibm.team.filesystem.cli.client.internal.sharecommand;

import com.ibm.team.filesystem.client.operations.IShareOutOfSync;
import com.ibm.team.filesystem.client.operations.OutOfSyncDilemmaHandler;
import com.ibm.team.filesystem.client.operations.ShareDilemmaHandler;
import java.util.Collection;








class CliShareDilemmaHandler
  extends ShareDilemmaHandler
{
  CliShareDilemmaHandler() {}
  
  public OutOfSyncDilemmaHandler getOutOfSyncDilemmaHandler()
  {
    new OutOfSyncDilemmaHandler()
    {

      public int outOfSync(Collection<IShareOutOfSync> sharesOutOfSync)
      {
        return 0;
      }
    };
  }
}
