package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;





public class FlowWorkspaceTargetUnsetCmdOptions
  implements IOptionSource
{
  public FlowWorkspaceTargetUnsetCmdOptions() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1), Messages.FlowTargetCmdOptions_WORKSPACE_HELP);
    
    return options;
  }
}
