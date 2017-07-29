package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;









public class ChangesetRelocateCmdOptions
  implements IOptionSource
{
  public ChangesetRelocateCmdOptions() {}
  
  public static final IPositionalOptionDefinition OPT_DST_CHANGESET = new PositionalOptionDefinition("destination", 1, 1, "@");
  

  public static final IPositionalOptionDefinition OPT_CHANGES = new PositionalOptionDefinition("changes", 1, -1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options opts = new Options(false, true);
    
    SubcommandUtil.addCredentialsToOptions(opts);
    
    opts
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.ChangesetRelocateCmdOptions_0)
      .addOption(OPT_DST_CHANGESET, Messages.ChangesetRelocateCmdOptions_1)
      .addOption(OPT_CHANGES, Messages.ChangesetRelocateCmdOptions_2);
    
    return opts;
  }
}
