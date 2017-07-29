package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;









public class ChangesetCommentCmdOptions
  implements IOptionSource
{
  public static final IPositionalOptionDefinition OPT_COMMENT = new PositionalOptionDefinition(Messages.ChangesetCommentCmdOptions_1, 1, 1);
  
  public ChangesetCommentCmdOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { Options opts = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(opts);
    
    opts.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP);
    
    opts.addOption(ChangesetCommonOptions.OPT_CHANGESET, Messages.ChangesetCommentCmdOptions_2);
    opts.addOption(OPT_COMMENT, Messages.ChangesetCommentCmdOptions_3);
    
    return opts;
  }
}
