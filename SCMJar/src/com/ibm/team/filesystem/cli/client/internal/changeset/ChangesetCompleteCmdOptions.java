package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;





public class ChangesetCompleteCmdOptions
  implements IOptionSource
{
  public ChangesetCompleteCmdOptions() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options opts = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(opts);
    
    opts.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP);
    
    opts.addOption(ChangesetCommonOptions.OPT_CHANGESETS, Messages.ChangesetCompleteCmdOptions_OPT_CHANGESET_HELP);
    opts.addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.ChangesetCompleteCmdOptions_0);
    
    return opts;
  }
}
