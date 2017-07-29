package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;












public class StatusCmdOpts
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_EXPAND_CHANGESET = new NamedOptionDefinition("C", "xchangeset", 0);
  

  public static final NamedOptionDefinition OPT_EXPAND_BASELINE = new NamedOptionDefinition("B", "xbaseline", 0);
  

  public static final NamedOptionDefinition OPT_EXPAND_ID = new NamedOptionDefinition("I", "xitem", 0);
  

  public static final NamedOptionDefinition OPT_INCLUDE = new NamedOptionDefinition("i", "include", 1);
  

  public static final NamedOptionDefinition OPT_EXCLUDE = new NamedOptionDefinition("x", "exclude", 1);
  

  public static final NamedOptionDefinition OPT_EXPAND_CURRENT_PORT = new NamedOptionDefinition(null, "xcurrentmerge", 0);
  

  public static final NamedOptionDefinition OPT_EXPAND_CURRENT_PORT_UNRESOLVED = new NamedOptionDefinition(null, "xunresolved", 0);
  
  public static final NamedOptionDefinition OPT_NO_LOCAL_REFRESH_DEPRECATED = new NamedOptionDefinition(
    "n", "no-local-refresh-deprecated", 0);
  

  public static final NamedOptionDefinition OPT_MAX_PORTS = new NamedOptionDefinition(null, "max-queue", 1);
  
  public StatusCmdOpts() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    options.setLongHelp(Messages.StatusCmdOpts_4);
    
    SubcommandUtil.addCredentialsToOptions(options);
    
    new MutuallyExclusiveGroup()
      .addOption(OPT_EXPAND_CURRENT_PORT, Messages.StatusCmdOpts_EXPAND_CURRENT_PORT, false)
      .addOption(OPT_EXPAND_CURRENT_PORT_UNRESOLVED, Messages.ListPortsCmdOption_Unresolved, false);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_WIDE, CommonOptions.OPT_WIDE_HELP)
      .addOption(CommonOptions.OPT_VERBOSE, Messages.StatusCmdOpts_3)
      .addOption(CommonOptions.OPT_ALL, Messages.StatusCmdOpts_OPT_ALL)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_INCLUDE, Messages.StatusCmdOpts_INCLUDE, false)
      .addOption(OPT_EXCLUDE, Messages.StatusCmdOpts_EXCLUDE, false))
      .addOption(OPT_EXPAND_BASELINE, Messages.StatusCmdOpts_2)
      .addOption(OPT_EXPAND_CHANGESET, Messages.StatusCmdOpts_1)
      .addOption(CommonOptions.OPT_MAX_CHANGES_INTERPRET, CommonOptions.OPT_MAX_CHANGES_INTERPRET_HELP)
      .addOption(OPT_EXPAND_ID, Messages.StatusCmdOpts_5)
      .addOption(OPT_NO_LOCAL_REFRESH_DEPRECATED, Messages.StatusCmdOpts_DO_NOT_SCAN_FS)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_EXPAND_CURRENT_PORT, Messages.StatusCmdOpts_EXPAND_CURRENT_PORT, false)
      .addOption(OPT_EXPAND_CURRENT_PORT_UNRESOLVED, Messages.ListPortsCmdOption_Unresolved, false))
      .addOption(OPT_MAX_PORTS, Messages.StatusCmdOpts_MAX_PORTS);
    

    OPT_NO_LOCAL_REFRESH_DEPRECATED.hideOption();
    
    return options;
  }
}
