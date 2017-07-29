package com.ibm.team.filesystem.cli.client.internal.preferences;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.util.List;
import org.eclipse.osgi.util.NLS;






public class PreferenceGetCmd
  extends PreferenceListCmd
  implements IOptionSource
{
  public static final OptionKey OPT_KEY = new OptionKey("key");
  
  public PreferenceGetCmd() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    options.addOption(CommonOptions.OPT_VERBOSE, Messages.PreferenceListCmd_VerboseHelp);
    options.addOption(
      new PositionalOptionDefinition(OPT_KEY, "key", 1, 1), Messages.PreferenceGetCmd_KeyHelp);
    return options;
  }
  
  protected String getKey(List<String> validPreferences)
    throws CLIFileSystemClientException
  {
    String key = config.getSubcommandCommandLine().getOption(OPT_KEY);
    if (!validPreferences.contains(key)) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.PreferenceGetCmd_InvalidKey, key, config
        .getContext().getAppName()));
    }
    return key;
  }
}
