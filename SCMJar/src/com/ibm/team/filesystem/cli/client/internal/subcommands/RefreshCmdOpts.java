package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;











public class RefreshCmdOpts
  implements IOptionSource
{
  public static final IOptionKey OPT_PATHS = new OptionKey("paths");
  public static final IPositionalOptionDefinition OPT_REFRESH = new PositionalOptionDefinition(OPT_PATHS, "paths", 1, -1);
  
  public RefreshCmdOpts() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(OPT_REFRESH, Messages.RefreshCmdOpts_OPT_PATHS_HELP);
    
    return options;
  }
}
