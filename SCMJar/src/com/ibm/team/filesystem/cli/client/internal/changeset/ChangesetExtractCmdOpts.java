package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;







public class ChangesetExtractCmdOpts
  implements IOptionSource
{
  public ChangesetExtractCmdOpts() {}
  
  public static final IPositionalOptionDefinition OPT_ITEMPATH = new PositionalOptionDefinition("path-to-item", 1, 1);
  

  public static final IPositionalOptionDefinition OPT_DISKPATH = new PositionalOptionDefinition("path-on-disk", 1, 1);
  

  public static final NamedOptionDefinition OPT_OVERWRITE = new NamedOptionDefinition("o", "overwrite", 0);
  
  public static final NamedOptionDefinition OPT_VER_STATE = new NamedOptionDefinition(null, "state", 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(OPT_OVERWRITE, Messages.ChangesetExtractCmdOpts_OVERWRITE)
      .addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.ChangesetCompleteCmdOptions_0)
      .addOption(OPT_VER_STATE, Messages.ChangesetExtractCmdOpts_VER_STATE)
      .addOption(ChangesetCommonOptions.OPT_CHANGESET, ChangesetCommonOptions.OPT_CHANGESET_HELP)
      .addOption(OPT_ITEMPATH, Messages.ChangesetExtractCmdOpts_PATH_TO_ITEM)
      .addOption(OPT_DISKPATH, Messages.ChangesetExtractCmdOpts_PATH_ON_DISK);
    
    return options;
  }
}
