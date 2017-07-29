package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;









public class FlowComponentTargetCmdOptions
  extends FlowWorkspaceTargetCmdOptions
{
  public static final PositionalOptionDefinition OPT_COMPONENT = new PositionalOptionDefinition("component", 1, 1);
  
  public FlowComponentTargetCmdOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { Options opts = super.getOptions();
    
    opts.addOption(OPT_COMPONENT, Messages.FlowComponentTargetCmdOptions_COMPONENT_OPTION_HELP);
    
    return opts;
  }
}
