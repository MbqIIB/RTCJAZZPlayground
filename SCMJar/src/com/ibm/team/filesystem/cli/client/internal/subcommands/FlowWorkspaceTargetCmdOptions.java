package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;








public class FlowWorkspaceTargetCmdOptions
  implements IOptionSource
{
  public FlowWorkspaceTargetCmdOptions() {}
  
  public static final PositionalOptionDefinition OPT_TARGET = new PositionalOptionDefinition("target-workspace", 1, 1, "@");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@"), Messages.FlowTargetCmdOptions_WORKSPACE_HELP)
      .addOption(OPT_TARGET, Messages.FlowTargetCmdOptions_TARGET_HELP);
    
    return options;
  }
}
