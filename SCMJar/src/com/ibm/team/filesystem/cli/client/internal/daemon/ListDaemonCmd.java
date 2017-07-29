package com.ibm.team.filesystem.cli.client.internal.daemon;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.internal.utils.IDaemonRegistry;
import com.ibm.team.filesystem.client.internal.utils.IDaemonRegistry.IDaemonSandbox;
import com.ibm.team.filesystem.client.internal.utils.IDaemonRegistry.IRegistryEntry;
import com.ibm.team.filesystem.client.restproxy.DaemonRegistry;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import java.io.File;
import java.util.Collection;

public class ListDaemonCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  private static final int COLWIDTH_PORT = 6;
  private static final int COLWIDTH_KEY = 34;
  
  public ListDaemonCmd() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options opts = new Options(false, true);
    
    opts.addOption(CommonOptions.OPT_VERBOSE, Messages.ListDaemonCmd_VERBOSE_OPTION_HELP);
    
    return opts;
  }
  

  public void run()
    throws FileSystemException
  {
    IDaemonRegistry reg = new DaemonRegistry(config.getConfigDirectory());
    boolean verbose = config.getSubcommandCommandLine().hasOption(CommonOptions.OPT_VERBOSE);
    
    Collection<IDaemonRegistry.IRegistryEntry> entries = reg.listDaemons(null);
    
    IndentingPrintStream root = new IndentingPrintStream(config.getContext().stdout());
    

    root.print(StringUtil.pad(Messages.ListDaemonCmd_COLUMN_LOCAL_PORT_NUMBER, 6));
    if (verbose) {
      root.print(StringUtil.pad(Messages.ListDaemonCmd_COLUMN_LOCAL_PASSWORD, 34));
    }
    
    root.print(Messages.ListDaemonCmd_COLUMN_DESCRIPTION_OF_DAEMON);
    
    root.println();
    

    for (IDaemonRegistry.IRegistryEntry entry : entries)
    {
      root.print(StringUtil.pad(Integer.toString(entry.getPort()), 6));
      
      if (verbose) {
        root.print(StringUtil.pad(entry.getKey(), 34));
      }
      
      String desc = entry.getDescription();
      root.print(desc == null ? "" : desc);
      
      root.println();
      
      IDaemonRegistry.IDaemonSandbox[] sandboxes = entry.getRegisteredSandboxes();
      if (sandboxes.length != 0)
      {



        IndentingPrintStream sbPrinter = root.indent();
        
        for (IDaemonRegistry.IDaemonSandbox sb : sandboxes) {
          sbPrinter.println(sb.getSandboxRoot().getAbsolutePath());
        }
      }
    }
  }
}
