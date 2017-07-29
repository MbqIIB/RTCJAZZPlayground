package com.ibm.team.filesystem.cli.client.internal.preferences;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.PreferenceRegistry;
import com.ibm.team.filesystem.cli.core.internal.ScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.ITypedPreferenceRegistry;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.util.Collection;
import java.util.Map;
import org.eclipse.osgi.util.NLS;


public class PreferenceSetCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public PreferenceSetCmd() {}
  
  public static final OptionKey OPT_KEY = new OptionKey("key");
  public static final OptionKey OPT_VALUE = new OptionKey("value");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false);
    options.addOption(new PositionalOptionDefinition(OPT_KEY, "key", 1, 1), Messages.PreferenceSetCmd_KeyHelp);
    options.addOption(new PositionalOptionDefinition(OPT_VALUE, "value", 1, 1), Messages.PreferenceSetCmd_ValueHelp);
    return options;
  }
  
  public void run() throws FileSystemException {
    Collection<String> validPreferences = PreferenceRegistry.getDefaultPreferences().keySet();
    String key = config.getSubcommandCommandLine().getOption(OPT_KEY);
    if (!validPreferences.contains(key)) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.PreferenceGetCmd_InvalidKey, key, config
        .getContext().getAppName()));
    }
    String value = config.getSubcommandCommandLine().getOption(OPT_VALUE);
    try {
      ((ScmClientConfiguration)config).getPersistentPreferences().setPreference(key, value);
    } catch (Exception e) {
      StatusHelper.failure(e.getLocalizedMessage(), e);
    }
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    out.println(NLS.bind(Messages.PropertySetCmd_KeySuccessfullySetToValue, key, value));
  }
}
