package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.util.ChoppingIndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.io.PrintStream;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.osgi.util.NLS;













public class StatusCmd
  extends AbstractSubcommand
{
  public StatusCmd() {}
  
  public static enum FilterType
  {
    INCLUSION,  EXCLUSION;
  }
  
  public static enum Direction { INCOMING("incoming", "in:"), 
    OUTGOING("outgoing", "out:");
    
    private final String directionValue;
    private final String directionName;
    
    private Direction(String directionName, String directionValue) {
      this.directionName = directionName;
      this.directionValue = directionValue;
    }
    
    public String getDirectionValue() {
      return directionValue;
    }
    
    public String getDirectionName() {
      return directionName;
    }
  }
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    if ((cli.hasOption(StatusCmdOpts.OPT_INCLUDE)) && (cli.hasOption(StatusCmdOpts.OPT_EXCLUDE)))
    {
      throw StatusHelper.failure(Messages.StatusCmd_BOTH_SPECIFIED, null);
    }
    
    if ((cli.hasOption(StatusCmdOpts.OPT_EXPAND_CURRENT_PORT)) && 
      (cli.hasOption(StatusCmdOpts.OPT_EXPAND_CURRENT_PORT_UNRESOLVED)))
    {
      throw StatusHelper.failure(NLS.bind(Messages.StatusCmd_BOTH_EXPAND_SPECIFIED, 
        StatusCmdOpts.OPT_EXPAND_CURRENT_PORT.toString(), 
        StatusCmdOpts.OPT_EXPAND_CURRENT_PORT_UNRESOLVED.toString()), null);
    }
    boolean opt_all = false;
    

    int chopsize = 80;
    boolean refreshLocal = true;
    
    ICommandLine subargs = config.getSubcommandCommandLine();
    opt_all = subargs.hasOption(CommonOptions.OPT_ALL);
    boolean verbose = (subargs.hasOption(CommonOptions.OPT_VERBOSE)) || (config.isJSONEnabled());
    boolean wide = (subargs.hasOption(CommonOptions.OPT_WIDE)) || (config.isJSONEnabled());
    chopsize = SubcommandUtil.getTerminalWidth(config);
    refreshLocal = (SubcommandUtil.shouldRefreshFileSystem(config)) && 
      (!cli.hasOption(StatusCmdOpts.OPT_NO_LOCAL_REFRESH_DEPRECATED));
    config.setEnableJSON(subargs.hasOption(CommonOptions.OPT_JSON));
    





    ResourcesPlugin.getWorkspace();
    
    JSONObject root = new JSONObject();
    
    SubcommandUtil.isCorrupt(root, verbose, config);
    
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.setVerbose(verbose);
    options.setFilterUnloadedComponents(!opt_all);
    options.enablePrinter(0);
    options.enablePrinter(1);
    options.enablePrinter(24);
    options.enablePrinter(25);
    options.enablePrinter(11);
    options.enablePrinter(17);
    options.enablePrinter(2);
    options.enablePrinter(9);
    options.enablePrinter(8);
    options.enablePrinter(10);
    options.enablePrinter(4);
    options.enablePrinter(22);
    options.enablePrinter(23);
    options.enablePrinter(15);
    options.enablePrinter(16);
    options.enablePrinter(5);
    options.enablePrinter(3);
    options.enablePrinter(40);
    
    enableCommonOptions(config, options);
    
    if ((cli.hasOption(StatusCmdOpts.OPT_INCLUDE)) || (cli.hasOption(StatusCmdOpts.OPT_EXCLUDE))) {
      options = filterOp(config, options);
    }
    
    options.setFilesystemScan(refreshLocal);
    
    IndentingPrintStream ps = 
      wide ? 
      new IndentingPrintStream(config.getContext().stdout()) : 
      new ChoppingIndentingPrintStream(config.getContext().stdout(), chopsize);
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    PendingChangesUtil.printPendingChanges(root, options, ps, client, config);
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(root);
    }
    
    if ((!config.isJSONEnabled()) && (options.isFilesystemOutOfSync())) {
      ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, LoadCmdLauncher.class);
      config.getContext().stdout().println(
        NLS.bind(
        Messages.CheckInCmd_9, 
        new String[] {
        config.getContext().getAppName(), 
        SubcommandUtil.getExecutionString(defnTemp).toString(), 
        ((OptionKey)LoadCmdOptions.OPT_FORCE).getName(), 
        ((OptionKey)LoadCmdOptions.OPT_RESYNC).getName() }));
    }
    

    if (options.isPartialStatus()) {
      throw StatusHelper.partialStatus();
    }
  }
  
  void enableCommonOptions(IScmClientConfiguration config, PendingChangesUtil.PendingChangesOptions opts) throws FileSystemException
  {
    ICommandLine subargs = config.getSubcommandCommandLine();
    
    boolean verbose = subargs.hasOption(CommonOptions.OPT_VERBOSE);
    boolean xchangeset = subargs.hasOption(StatusCmdOpts.OPT_EXPAND_CHANGESET);
    boolean xbaseline = subargs.hasOption(StatusCmdOpts.OPT_EXPAND_BASELINE);
    boolean xitem = subargs.hasOption(StatusCmdOpts.OPT_EXPAND_ID);
    boolean xcurrentport = subargs.hasOption(StatusCmdOpts.OPT_EXPAND_CURRENT_PORT);
    boolean xunresolved = subargs.hasOption(StatusCmdOpts.OPT_EXPAND_CURRENT_PORT_UNRESOLVED);
    int maxChanges = -1;
    maxChanges = CommonOptions.getMaxChangesToInterpret(config);
    

    if ((xchangeset) || (config.isJSONEnabled())) {
      opts.enablePrinter(7);
      opts.enablePrinter(21);
      opts.enablePrinter(6);
      opts.enablePrinter(31);
      opts.setMaxChanges(maxChanges);
    }
    
    if ((xbaseline) || (config.isJSONEnabled())) {
      opts.enablePrinter(12);
    }
    
    if ((xitem) || (config.isJSONEnabled())) {
      opts.enablePrinter(14);
    }
    
    if ((xcurrentport) || (xunresolved) || (config.isJSONEnabled())) {
      opts.enablePrinter(32);
    }
    
    if ((xunresolved) || (config.isJSONEnabled())) {
      opts.enablePrinter(33);
    }
    
    ICommandLine cli = config.getSubcommandCommandLine();
    opts.setMaxPorts(RepoUtil.getMaxResultsOption(cli, StatusCmdOpts.OPT_MAX_PORTS, Messages.StatusCmd_MAX_PORTS_NUMBER_FORMAT_EXCEPTION));
    
    if ((verbose) || (config.isJSONEnabled())) {
      opts.enablePrinter(13);
    }
  }
  

  class Filter
  {
    StatusCmd.FilterType filterType;
    
    PendingChangesUtil.PendingChangesOptions opts;
    
    Filter()
    {
      filterType = StatusCmd.FilterType.INCLUSION;
      
      opts = new PendingChangesUtil.PendingChangesOptions();
    }
    
    Filter(PendingChangesUtil.PendingChangesOptions options)
    {
      filterType = StatusCmd.FilterType.EXCLUSION;
      
      opts = options;
    }
    

    void filter(int item)
    {
      if (filterType == StatusCmd.FilterType.EXCLUSION)
      {
        opts.disablePrinter(item);
      }
      else
      {
        opts.enablePrinter(item);
      }
    }
    
    PendingChangesUtil.PendingChangesOptions mapToPendingChangesOptions(IScmClientConfiguration config) throws FileSystemException
    {
      opts.enablePrinter(0);
      
      if ((filterType == StatusCmd.FilterType.INCLUSION) && ((opts.isPrinterEnabled(16)) || 
        (opts.isPrinterEnabled(15)) || 
        (opts.isPrinterEnabled(9)) || 
        (opts.isPrinterEnabled(8))))
      {

        opts.enablePrinter(1);
        

        if ((opts.isPrinterEnabled(16)) || 
          (opts.isPrinterEnabled(15)))
        {
          opts.enablePrinter(11);
        }
        
        opts.enablePrinter(4);
        opts.enablePrinter(22);
        opts.enablePrinter(23);
        opts.enablePrinter(5);
        
        enableCommonOptions(config, opts);
      }
      
      return opts;
    }
  }
  
  PendingChangesUtil.PendingChangesOptions filterOp(IScmClientConfiguration config, PendingChangesUtil.PendingChangesOptions options)
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    String str = cli.hasOption(StatusCmdOpts.OPT_INCLUDE) ? cli.getOption(StatusCmdOpts.OPT_INCLUDE) : cli.getOption(StatusCmdOpts.OPT_EXCLUDE);
    
    Filter filt = null;
    
    if (cli.hasOption(StatusCmdOpts.OPT_INCLUDE)) {
      filt = new Filter();
    }
    else {
      filt = new Filter(options);
    }
    

    Direction currentFilterDir = Direction.INCOMING;
    char nextDirChar = ' ';
    int idx = 0;
    boolean endofDir = false;
    
    str = str.trim().replace("\"", "");
    

    if ((str.length() > 2) && (str.substring(0, 3).equals(Direction.INCOMING.getDirectionValue()))) {
      nextDirChar = 'o';
      idx = 3;
    }
    else if ((str.length() > 3) && (str.substring(0, 4).equals(Direction.OUTGOING.getDirectionValue())))
    {
      currentFilterDir = Direction.OUTGOING;
      nextDirChar = 'i';
      idx = 4;
    } else {
      throw StatusHelper.failure(NLS.bind(Messages.StatusCmd_INVALID_DIRECTION, 
        new String[] { Direction.INCOMING.getDirectionValue(), Direction.OUTGOING.getDirectionValue() }), 
        null);
    }
    

    if (str.length() <= idx) {
      throw StatusHelper.failure(NLS.bind(Messages.StatusCmd_NO_FILTER_VALUE, currentFilterDir.getDirectionName()), null);
    }
    
    do
    {
      if (str.charAt(idx) == 'c')
      {
        if (currentFilterDir == Direction.INCOMING) {
          filt.filter(9);
        } else {
          filt.filter(8);
        }
      }
      else if (str.charAt(idx) == 'b')
      {
        if (currentFilterDir == Direction.INCOMING) {
          filt.filter(16);
        } else {
          filt.filter(15);
        }
      }
      else if (str.charAt(idx) == 'C')
      {
        if (currentFilterDir == Direction.INCOMING) {
          filt.filter(24);
        } else {
          filt.filter(25);
        }
      }
      else if (str.charAt(idx) == nextDirChar)
      {
        if (nextDirChar == 'z') {
          continue;
        }
        
        boolean fail = false;
        

        if (nextDirChar == 'i') {
          if ((str.length() <= idx + 2) || (!Direction.INCOMING.getDirectionValue().equals(str.substring(idx, idx + 3)))) {
            fail = true;
          } else {
            currentFilterDir = Direction.INCOMING;
            idx += 2;
          }
        } else if (nextDirChar == 'o')
        {
          if ((str.length() <= idx + 3) || (!Direction.OUTGOING.getDirectionValue().equals(str.substring(idx, idx + 4)))) {
            fail = true;
          } else {
            currentFilterDir = Direction.OUTGOING;
            idx += 3;
          }
        }
        
        if ((fail) || (endofDir)) {
          throw StatusHelper.failure(NLS.bind(Messages.StatusCmd_INVALID_DIRECTION, 
            new String[] { Direction.INCOMING.getDirectionValue(), Direction.OUTGOING.getDirectionValue() }), null);
        }
        

        if (str.length() <= idx + 1) {
          throw StatusHelper.failure(NLS.bind(Messages.StatusCmd_NO_FILTER_VALUE, currentFilterDir.getDirectionName()), null);
        }
        
        endofDir = true;
        nextDirChar = 'z';
      }
      
      idx++;
    }
    while (idx < str.length());
    
    return filt.mapToPendingChangesOptions(config);
  }
}
