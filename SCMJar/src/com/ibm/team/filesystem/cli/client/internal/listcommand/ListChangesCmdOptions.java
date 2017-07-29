package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;







public class ListChangesCmdOptions
  implements IOptionSource
{
  public ListChangesCmdOptions() {}
  
  public static final PositionalOptionDefinition OPT_CHANGESET = new PositionalOptionDefinition("change-set", 1, -1, "@");
  

  public static final NamedOptionDefinition OPT_WORKSPACE = new NamedOptionDefinition("w", "workspace", 1, "@");
  

  public static final NamedOptionDefinition OPT_SNAPSHOT = new NamedOptionDefinition("s", "snapshot", 1, "@");
  

  public static final NamedOptionDefinition OPT_BASELINE = new NamedOptionDefinition("b", "baseline", 1, "@");
  
  public static final IOptionKey OPT_SHOW_SHORT_VERSION_ID = new OptionKey("show-versionid");
  public static final IOptionKey OPT_SHOW_FULL_VERSION_ID = new OptionKey("show-full-versionid");
  

  public static final NamedOptionDefinition OPT_INCLUDE_UNCHANGED_FILES = new NamedOptionDefinition(null, "includeUnchangedFiles", 0, null);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(OPT_WORKSPACE, Messages.ListChangesetCmdOptions_WORKSPACE_HELP)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_SNAPSHOT, Messages.ListChangesetCmdOptions_SNAPSHOT_HELP, false)
      .addOption(OPT_BASELINE, Messages.ListChangesetCmdOptions_BASELINE_HELP, false))
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_SHOW_SHORT_VERSION_ID, "i", "show-versionid", Messages.ListChangesCmdOptions_SHORT_VID_HELP, 0, false)
      .addOption(OPT_SHOW_FULL_VERSION_ID, "f", "show-full-versionid", Messages.ListChangesCmdOptions_FULL_VID_HELP, 0, false))
      .addOption(OPT_CHANGESET, ChangesetCommonOptions.OPT_CHANGESET_HELP)
      .addOption(OPT_INCLUDE_UNCHANGED_FILES, Messages.Common_INCLUDE_UNCHANGED_FILES);
    
    return options;
  }
}
