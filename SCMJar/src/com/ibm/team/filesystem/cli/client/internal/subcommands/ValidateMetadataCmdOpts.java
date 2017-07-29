package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;








public class ValidateMetadataCmdOpts
  implements IOptionSource
{
  public ValidateMetadataCmdOpts() {}
  
  public static final PositionalOptionDefinition OPT_LOGFILE = new PositionalOptionDefinition("logfile", 0, 1);
  public static final NamedOptionDefinition OPT_DUMP = new NamedOptionDefinition("i", "include", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(true);
    options.setLongHelp(Messages.ValidateMetadataCmd_0);
    options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP);
    options.addOption(OPT_LOGFILE, Messages.ValidateMetadataCmd_3);
    options.addOption(OPT_DUMP, Messages.ValidateMetadataCmd_4);
    
    return options;
  }
}
