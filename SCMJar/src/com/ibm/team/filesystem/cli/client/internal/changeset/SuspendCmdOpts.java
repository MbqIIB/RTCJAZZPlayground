package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;












public class SuspendCmdOpts
  implements IOptionSource
{
  public SuspendCmdOpts() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.DiscardCmdOpts_0);
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, CommonOptions.OPT_VERBOSE_HELP)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(CommonOptions.OPT_MAX_CHANGES_INTERPRET, CommonOptions.OPT_MAX_CHANGES_INTERPRET_HELP)
      .addOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED, Messages.Common_FORCE_OVERWRITE_UNCOMMITTED)
      .addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.ChangesetAssociateWorkitemOptions_1)
      .addOption(CommonOptions.OPT_CHANGESET_SELECTORS, Messages.SuspendCmdOpts_0)
      .addOption(CommonOptions.OPT_RELEASE_LOCK, Messages.SuspendCmdOpts_1);
    
    return options;
  }
}
