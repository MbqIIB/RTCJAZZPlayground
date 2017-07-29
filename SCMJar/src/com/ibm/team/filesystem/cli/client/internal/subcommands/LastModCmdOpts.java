package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;








public class LastModCmdOpts
  implements IOptionSource
{
  public LastModCmdOpts() {}
  
  public static final PositionalOptionDefinition OPT_TO_CALCULATE = new PositionalOptionDefinition("path", 1, -1);
  public static final NamedOptionDefinition OPT_DATE_FORMAT = new NamedOptionDefinition("f", "format", 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.LastModCmdOpts_0);
    
    SubcommandUtil.addCredentialsToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(OPT_DATE_FORMAT, Messages.LastModCmdOpts_1)
      .addOption(OPT_TO_CALCULATE, Messages.LastModCmdOpts_2);
    
    return options;
  }
}
