package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptCmdOptions;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;















public class ResumeCmdOptions
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_RESUME_MISSING_CHANGESETS = new NamedOptionDefinition(null, "resume-missing-changesets", 0);
  
  public ResumeCmdOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    options.setLongHelp(Messages.ResumeCmdOptions_0);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, CommonOptions.OPT_VERBOSE_HELP)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER, Messages.AcceptCmdOptions_1, false)
      .addOption(AcceptCmdOptions.OPT_DISABLE_AUTOMERGE, Messages.AcceptCmdOptions_DISABLE_AUTOMERGE_HELP, false))
      .addOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED, Messages.ResumeCmdOptions_OPT_OVERWRITE_UNCOMMITTED_HELP)
      .addOption(AcceptCmdOptions.OPT_ACCEPT_WITH_PORTING, Messages.AcceptCmdOptions_Accept_With_Porting_Help)
      .addOption(OPT_RESUME_MISSING_CHANGESETS, Messages.ResumeCmdOptions_RESUME_MISSING_CHANGESETS)
      .addOption(CommonOptions.OPT_STREAM_TARGET_SELECTOR, "t", "target", 
      Messages.ResumeCmdOptions_4, 1)
      .addOption(CommonOptions.OPT_CHANGESET_SELECTORS, Messages.ResumeCmdOptions_2)
      .addOption(CommonOptions.OPT_ACQUIRE_LOCK, Messages.ResumeCmdOptions_5);
    

    return options;
  }
}
