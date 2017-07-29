package com.ibm.team.filesystem.cli.client.internal.snapshot;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;






public class PromoteSnapshotCmdOpts
  implements IOptionSource
{
  public PromoteSnapshotCmdOpts() {}
  
  public static final IPositionalOptionDefinition OPT_SNAPSHOTS = new PositionalOptionDefinition(CommonOptions.OPT_SNAPSHOTS, "snapshots", 1, -1, "@");
  public static final IPositionalOptionDefinition OPT_TARGET = new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options.addOption(OPT_TARGET, Messages.ReplaceComponentsCmdOptions_TARGET_WORKSPACE)
      .addOption(OPT_SNAPSHOTS, Messages.PromoteSnapshotCmdOpts_OPT_PROMOTE_HELP);
    
    return options;
  }
}
