package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;










public class ChangesetDiscardCmdOpts
  implements IOptionSource
{
  public ChangesetDiscardCmdOpts() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.DiscardCmdOpts_0);
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(CommonOptions.OPT_VERBOSE, Messages.DiscardCmdOpts_VERBOSE)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED, Messages.Common_FORCE_OVERWRITE_UNCOMMITTED)
      .addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.ChangesetAssociateWorkitemOptions_1)
      .addOption(CommonOptions.OPT_CHANGESET_SELECTORS, Messages.DiscardCmdOpts_1)
      .addOption(CommonOptions.OPT_RELEASE_LOCK, Messages.DiscardCmdOpts_2);
    
    return options;
  }
}
