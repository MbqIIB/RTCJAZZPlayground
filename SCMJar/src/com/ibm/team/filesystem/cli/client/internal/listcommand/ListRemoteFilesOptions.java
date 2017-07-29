package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
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
import org.eclipse.osgi.util.NLS;









public class ListRemoteFilesOptions
  implements IOptionSource
{
  public static final PositionalOptionDefinition OPT_SELECTOR = new PositionalOptionDefinition("selector", 1, 1, "@");
  public static final NamedOptionDefinition OPT_WORKSPACE = new NamedOptionDefinition("w", "workspace", 0);
  public static final NamedOptionDefinition OPT_SNAPSHOT = new NamedOptionDefinition("s", "snapshot", 0);
  public static final NamedOptionDefinition OPT_BASELINE = new NamedOptionDefinition("b", "baseline", 0);
  public static final NamedOptionDefinition OPT_SHOW_ACCESS = new NamedOptionDefinition("a", "show-access", 0);
  public static final IOptionKey OPT_SHOW_SHORT_VERSION_ID = new OptionKey("show-versionid");
  public static final IOptionKey OPT_SHOW_FULL_VERSION_ID = new OptionKey("show-full-versionid");
  public static final IOptionKey OPT_REMOTE_PATH = new OptionKey("remotePath");
  public static final IOptionKey OPT_DEPTH = new OptionKey("depth");
  public static final PositionalOptionDefinition OPT_COMPONENT_SELECTOR = new PositionalOptionDefinition("component", 1, 1);
  
  public ListRemoteFilesOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(new NamedOptionDefinition(OPT_DEPTH, null, "depth", 1), Messages.ListRemoteFilesOptions_2)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_SHOW_SHORT_VERSION_ID, "i", "show-versionid", Messages.ListRemoteFilesOptions_SHOW_VERSION_ID_HELP, 0, false)
      .addOption(OPT_SHOW_FULL_VERSION_ID, "f", "show-full-versionid", Messages.ListRemoteFilesOptions_SHOW_FULL_VERSION_ID_HELP, 0, false))
      
      .addOption(OPT_SHOW_ACCESS, Messages.ListRemoteFilesOptions_ACCESS_HELP, false)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_WORKSPACE, Messages.ListRemoteFilesOptions_WORKSPACE_HELP, true)
      .addOption(OPT_SNAPSHOT, Messages.ListRemoteFilesOptions_SNAPSHOT_HELP, true)
      .addOption(OPT_BASELINE, Messages.ListRemoteFilesOptions_BASELINE_HELP, true))
      .addOption(OPT_SELECTOR, NLS.bind(Messages.ListRemoteFilesOptions_SELECTOR_HELP, 
      new Object[] { OPT_WORKSPACE.getName(), OPT_SNAPSHOT.getName(), OPT_BASELINE.getName() }))
      .addOption(OPT_COMPONENT_SELECTOR, Messages.ListRemoteFilesOptions_0)
      .addOption(new PositionalOptionDefinition(OPT_REMOTE_PATH, "remotePath", 0, 1), 
      Messages.ListRemoteFilesOptions_1);
    
    return options;
  }
}
