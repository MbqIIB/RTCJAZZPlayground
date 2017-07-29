package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.IRelativeLocation;
import com.ibm.team.filesystem.client.operations.IDownloadListener;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.osgi.util.NLS;















public class DownloadProgressWriter
  implements IDownloadListener
{
  final Job job = new Job("ProgressWriter")
  {
    protected IStatus run(IProgressMonitor monitor)
    {
      return showMessages(monitor);
    }
  };
  

  private final boolean verbose;
  
  private final boolean quiet;
  
  private final PrintStream out;
  
  private final PrintStream err;
  

  public DownloadProgressWriter(PrintStream out, PrintStream err, boolean verbose, boolean quiet)
  {
    this.out = out;
    this.err = err;
    this.verbose = verbose;
    this.quiet = quiet;
  }
  

  final LinkedList<String> toWrite = new LinkedList();
  
  public void downloadStarted(ILocation sandboxRoot, IRelativeLocation relativePath, long sizeInBytes)
  {
    if (quiet) {
      return;
    }
    
    synchronized (toWrite) {
      toWrite.add(NLS.bind(Messages.DownloadProgressWriter_1, relativePath.toString(), 
        bytesAsHumanReadable(sizeInBytes)));
    }
    
    job.schedule();
  }
  
  protected IStatus showMessages(IProgressMonitor monitor)
  {
    for (;;)
    {
      try
      {
        String message;
        synchronized (toWrite) {
          message = (String)toWrite.removeLast();
        }
      }
      catch (NoSuchElementException localNoSuchElementException) {
        return Status.OK_STATUS;
      }
      String message;
      out.println(message);
    }
  }
  



  static final double[] statisticalDivisors = { 1024.0D, 1048576.0D, 
    1.073741824E9D, 1.099511627776E12D, 1.125899906842624E15D };
  
  static final String[] statisticalSuffixes = { Messages.DownloadProgressWriter_2, Messages.DownloadProgressWriter_3, Messages.DownloadProgressWriter_4, Messages.DownloadProgressWriter_5, Messages.DownloadProgressWriter_6 };
  
  private static String bytesAsHumanReadable(long amt)
  {
    for (int i = statisticalDivisors.length - 1; i >= 0; i--)
    {
      if (amt > statisticalDivisors[i]) {
        return String.format("%,.1f %s", new Object[] { Double.valueOf(amt / statisticalDivisors[i]), 
          statisticalSuffixes[i] });
      }
    }
    

    return NLS.bind(Messages.DownloadProgressWriter_7, Long.valueOf(amt));
  }
  
  public void join()
  {
    try {
      job.join();
    } catch (InterruptedException localInterruptedException) {
      err.println(Messages.DownloadProgressWriter_8);
    }
  }
}
