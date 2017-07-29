package com.ibm.team.filesystem.cli.client.internal.createcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;











public class CreateWorkspaceOptions
  extends CreateWorkspaceBaseOptions
{
  public static final IOptionKey OPT_EMPTY = new OptionKey("empty");
  
  public CreateWorkspaceOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { MutuallyExclusiveGroup grp = super.getMutuallyExclusiveOptions();
    grp.addOption(new NamedOptionDefinition(OPT_EMPTY, "e", "empty", 0), Messages.CreateWorkspaceCmd_6, false);
    
    Options options = new Options(false, true);
    options
      .addOption(new ContinuousGroup()
      .addOption(CommonOptions.OPT_URI, CommonOptions.OPT_URI_HELP, false)
      .addOption(SubcommandUtil.getCredentialsGroup(false))
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT, false)
      .addOption(OPT_DESC, Messages.CreateWorkspaceCmd_11, false)
      .addOption(grp)
      .addOption(new PositionalOptionDefinition(OPT_NAME, "name", 1, 1, "@"), getNameHelp(), true))
      .addOption(new ContinuousGroup()
      .addOption(CommonOptions.OPT_URI, CommonOptions.OPT_URI_HELP, false)
      .addOption(SubcommandUtil.getCredentialsGroup(false))
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT, false)
      .addOption(new NamedOptionDefinition(OPT_DUPLICATE, null, "duplicate", 1, "@"), getDuplicateHelp(), true)
      .addOption(new PositionalOptionDefinition(OPT_NAME, "name", 1, 1, "@"), getNameHelp(), true));
    
    return options;
  }
  
  public String getNameHelp()
  {
    return Messages.CreateWorkspaceCmd_5;
  }
  
  public String getFlowTargetHelp()
  {
    return Messages.CreateWorkspaceCmd_8;
  }
  
  public String getSnapshotHelp()
  {
    return Messages.CreateWorkspaceCmd_15;
  }
  
  public String getDuplicateHelp()
  {
    return Messages.CreateWorkspaceCmdOptions_Duplicate;
  }
}
