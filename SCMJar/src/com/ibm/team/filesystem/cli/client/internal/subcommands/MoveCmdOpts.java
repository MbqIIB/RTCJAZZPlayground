package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;










public class MoveCmdOpts
  implements IOptionSource
{
  public static final PositionalOptionDefinition OPT_SOURCE = new PositionalOptionDefinition("source", 1, 1);
  

  public static final PositionalOptionDefinition OPT_TARGET = new PositionalOptionDefinition("target", 1, 1);
  
  public MoveCmdOpts() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    options.setLongHelp(Messages.MoveCmdOpts_0);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(OPT_SOURCE, Messages.MoveCmdOpts_1)
      .addOption(OPT_TARGET, Messages.MoveCmdOpts_2);
    
    return options;
  }
}
