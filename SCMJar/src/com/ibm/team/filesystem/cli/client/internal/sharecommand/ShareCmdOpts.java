package com.ibm.team.filesystem.cli.client.internal.sharecommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;









public class ShareCmdOpts
  implements IOptionSource
{
  public ShareCmdOpts() {}
  
  public static final PositionalOptionDefinition OPT_WORKSPACE_SELECTOR = new PositionalOptionDefinition("workspace", 1, 1, "@");
  public static final PositionalOptionDefinition OPT_COMPONENT_SELECTOR = new PositionalOptionDefinition("component", 1, 1);
  public static final PositionalOptionDefinition OPT_TO_SHARE = new PositionalOptionDefinition("path", 1, -1);
  
  public static final NamedOptionDefinition OPT_IGNORE_EXISTING_SHARE = new NamedOptionDefinition(null, "reshare", 0);
  

  public static final OptionKey OPT_REMOTE_PATH = new OptionKey("remotePath");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.ShareCmdOpts_0);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT);
    options.addOption(CommonOptions.OPT_VERBOSE, Messages.ShareCmdOpts_1);
    options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP);
    options.addOption(OPT_IGNORE_EXISTING_SHARE, Messages.ShareCmdOpts_5);
    options.addOption(OPT_REMOTE_PATH, "R", "remotePath", Messages.ShareCmdOpts_6, 1);
    options.addOption(OPT_WORKSPACE_SELECTOR, Messages.ShareCmdOpts_2);
    options.addOption(OPT_COMPONENT_SELECTOR, Messages.ShareCmdOpts_3);
    options.addOption(OPT_TO_SHARE, Messages.ShareCmdOpts_4);
    
    return options;
  }
}
