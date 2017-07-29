package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;








public class AnnotateCmdOptions
  implements IOptionSource
{
  public AnnotateCmdOptions() {}
  
  public static final PositionalOptionDefinition OPT_PATH = new PositionalOptionDefinition("path", 1, 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.AnnotateCmdOptions_HELP);
    
    SubcommandUtil.addCredentialsToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(OPT_PATH, Messages.AnnotateCmdOptions_PATH);
    
    return options;
  }
}
