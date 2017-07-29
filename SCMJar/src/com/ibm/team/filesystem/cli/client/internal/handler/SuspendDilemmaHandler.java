package com.ibm.team.filesystem.cli.client.internal.handler;

import com.ibm.team.filesystem.cli.core.util.CLIBackupHandler;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocalChange;
import com.ibm.team.filesystem.client.internal.utils.ConfigurationFacade;
import com.ibm.team.filesystem.client.operations.BackupDilemmaHandler;
import java.util.Collection;
import java.util.Map;










public class SuspendDilemmaHandler
  extends com.ibm.team.filesystem.client.operations.SuspendDilemmaHandler
{
  private UncheckedInChangesHandler uncheckedInChangesHandler;
  public CLIBackupHandler backupHandler = new CLIBackupHandler();
  
  public SuspendDilemmaHandler(int uncheckedInChanges) {
    uncheckedInChangesHandler = new UncheckedInChangesHandler(uncheckedInChanges);
  }
  

  public int uncheckedInChanges(Map<ConfigurationFacade, Collection<ILocalChange>> changes)
  {
    return uncheckedInChangesHandler.uncheckedInChanges(changes);
  }
  
  public void throwExceptions() throws FileSystemException {
    uncheckedInChangesHandler.throwExceptions();
  }
  
  public BackupDilemmaHandler getBackupDilemmaHandler()
  {
    return backupHandler;
  }
}
