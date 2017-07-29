package com.ibm.team.filesystem.cli.client.internal.querycommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;








public class QueryOptions
  implements IOptionSource
{
  public static final IOptionKey OPT_SHOW_SHORT_VERSION_ID = new OptionKey("show-versionid");
  public static final IOptionKey OPT_SHOW_FULL_VERSION_ID = new OptionKey("show-full-versionid");
  public static final IOptionKey OPT_QUERY_STR = new OptionKey("query");
  
  public static final NamedOptionDefinition OPT_WORKSPACE = new NamedOptionDefinition("w", 
    "workspace", 0);
  public static final NamedOptionDefinition OPT_STREAM = new NamedOptionDefinition("S", 
    "stream", 0);
  public static final NamedOptionDefinition OPT_SNAPSHOT = new NamedOptionDefinition("s", 
    "snapshot", 0);
  public static final NamedOptionDefinition OPT_COMPONENT = new NamedOptionDefinition("C", 
    "component", 0);
  public static final NamedOptionDefinition OPT_BASELINE = new NamedOptionDefinition("b", 
    "baseline", 0);
  public static final NamedOptionDefinition OPT_FILES = new NamedOptionDefinition("v", 
    "file", 0);
  
  public QueryOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(new PositionalOptionDefinition(OPT_QUERY_STR, "query string", 1, 1), Messages.QueryVersionableCmdOptions_QUERY_STR_HELP)
      .addOption(new ContinuousGroup()
      .addOption(OPT_SHOW_SHORT_VERSION_ID, "i", "show-versionid", Messages.ListRemoteFilesOptions_SHOW_VERSION_ID_HELP, 0, false)
      .addOption(OPT_SHOW_FULL_VERSION_ID, "f", "show-full-versionid", Messages.ListRemoteFilesOptions_SHOW_FULL_VERSION_ID_HELP, 0, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_FILES, Messages.QueryCmd_FILE_HELP, false)
      .addOption(OPT_SHOW_SHORT_VERSION_ID, "i", "show-versionid", Messages.ListRemoteFilesOptions_SHOW_VERSION_ID_HELP, 0, false)
      .addOption(OPT_SHOW_FULL_VERSION_ID, "f", "show-full-versionid", Messages.ListRemoteFilesOptions_SHOW_FULL_VERSION_ID_HELP, 0, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_STREAM, Messages.QueryCmd_STREAM_HELP, false)
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_WORKSPACE, Messages.QueryCmd_WORKSPACE_HELP, false)
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_BASELINE, Messages.QueryCmd_BASELINE_HELP, false)
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_SNAPSHOT, Messages.QueryCmd_SNAPSHOT_HELP, false)
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_COMPONENT, Messages.QueryCmd_COMPONENT_HELP, false)
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP, false));
    

    return options;
  }
}
