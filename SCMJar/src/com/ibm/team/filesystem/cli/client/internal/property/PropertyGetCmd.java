package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;








public class PropertyGetCmd
  extends PropertyListCmd
  implements IOptionSource
{
  public PropertyGetCmd() {}
  
  public static final OptionKey OPT_KEY = new OptionKey("key");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(new PositionalOptionDefinition(OPT_KEY, "key", 1, 1), Messages.PropertyGetCmd_KeyHelp)
      .addOption(new PositionalOptionDefinition(OPT_FILES, "files", 1, -1), 
      Messages.PropertyGetCmd_FilesHelp);
    return options;
  }
  
  public String getKey(ICommandLine cli)
  {
    return cli.getOption(OPT_KEY);
  }
}
