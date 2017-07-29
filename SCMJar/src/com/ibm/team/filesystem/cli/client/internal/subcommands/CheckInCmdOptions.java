package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;









public class CheckInCmdOptions
  implements IOptionSource
{
  public CheckInCmdOptions() {}
  
  public static final PositionalOptionDefinition OPT_TO_COMMIT = new PositionalOptionDefinition("path", 1, -1);
  
  public static final NamedOptionDefinition OPT_COMMIT_TARGET = new NamedOptionDefinition("c", "change-set", 1);
  
  public static final NamedOptionDefinition OPT_DELIM_CONSISTENT = new NamedOptionDefinition("C", "delim-consistent", 0);
  
  public static final NamedOptionDefinition OPT_DELIM_NONE = new NamedOptionDefinition("n", "delim-none", 0);
  
  public static final NamedOptionDefinition OPT_COMMIT_DELETED_CONTENT = new NamedOptionDefinition("D", "checkin-deleted", 0);
  public static final NamedOptionDefinition OPT_SKIP_DELETED_CONTENT = new NamedOptionDefinition("S", "skip-deleted", 0);
  
  public static final NamedOptionDefinition OPT_COMMENT = new NamedOptionDefinition(null, "comment", 1);
  public static final NamedOptionDefinition OPT_COMPLETE = new NamedOptionDefinition(null, "complete", 0);
  public static final NamedOptionDefinition OPT_WORKITEM = new NamedOptionDefinition("W", "workitem", 1, "@");
  
  public static final NamedOptionDefinition OPT_CURRENT_MERGE = new NamedOptionDefinition(null, "current-merge", 0);
  public static final NamedOptionDefinition OPT_IGNORE_LOCAL_CONFLICTS = new NamedOptionDefinition("i", "ignore-local-conflicts", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.CheckInCmdOptions_0);
    
    SubcommandUtil.addCredentialsToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, Messages.CheckInCmdOptions_OPT_HELP_VERBOSE)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(CommonOptions.OPT_MAX_CHANGES_INTERPRET, CommonOptions.OPT_MAX_CHANGES_INTERPRET_HELP)
      .addOption(OPT_DELIM_CONSISTENT, Messages.CheckInCmdOptions_OPT_HELP_FORCE_CONSISTENT)
      .addOption(OPT_DELIM_NONE, Messages.CheckInCmdOptions_OPT_HELP_FORCE_NONE_DELIM_PROPERTY)
      .addOption(OPT_COMMIT_DELETED_CONTENT, Messages.CheckInCmdOptions_OPT_HELP_FORCE_COMMIT_DELETED_CONTENT)
      .addOption(OPT_SKIP_DELETED_CONTENT, Messages.CheckInCmdOptions_OPT_HELP_FORCE_SKIP_DELETED_CONTENT)
      .addOption(OPT_CURRENT_MERGE, Messages.CheckInCmdOptions_OPT_HELP_CURRENT_MERGE)
      .addOption(OPT_COMMENT, Messages.ChangesetCommentCmdOptions_3)
      .addOption(OPT_COMPLETE, Messages.CheckInCmdOptions_Complete)
      .addOption(OPT_WORKITEM, Messages.ChangesetAssociateWorkitemOptions_0)
      .addOption(OPT_COMMIT_TARGET, Messages.CheckInCmdOptions_5)
      .addOption(OPT_IGNORE_LOCAL_CONFLICTS, Messages.CheckInCmdOptions_OPT_HELP_IGNORE_LOCAL_CONFLICTS)
      .addOption(OPT_TO_COMMIT, Messages.CheckInCmdOptions_4);
    
    return options;
  }
}
