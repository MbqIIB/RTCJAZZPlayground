package com.ibm.team.filesystem.cli.client.internal.flowcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;









public class RefreshHelper
{
  public RefreshHelper() {}
  
  public static void refresh(IPath cfaRoot)
    throws FileSystemException
  {
    try
    {
      ResourcesPlugin.getWorkspace().getRoot().refreshLocal(
        2, null);
    } catch (CoreException e) {
      throw StatusHelper.failure(Messages.RefreshHelper_0, 
        e);
    }
  }
}
