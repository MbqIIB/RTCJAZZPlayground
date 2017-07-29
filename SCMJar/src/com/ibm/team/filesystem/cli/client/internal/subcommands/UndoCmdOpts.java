package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;












public class UndoCmdOpts
  implements IOptionSource
{
  public UndoCmdOpts() {}
  
  public static final PositionalOptionDefinition OPT_CHANGES = new PositionalOptionDefinition(Messages.UndoCmdOpts_1, 1, -1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.UndoCmdOpts_0);
    
    SubcommandUtil.addCredentialsToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(CommonOptions.OPT_VERBOSE, CommonOptions.OPT_VERBOSE_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(OPT_CHANGES, Messages.UndoCmdOpts_2)
      .addOption(CommonOptions.OPT_RELEASE_LOCK, Messages.UndoCmdOpts_3);
    
    return options;
  }
}
