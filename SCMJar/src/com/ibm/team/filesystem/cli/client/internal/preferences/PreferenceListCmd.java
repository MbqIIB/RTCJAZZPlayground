package com.ibm.team.filesystem.cli.client.internal.preferences;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.PreferenceRegistry;
import com.ibm.team.filesystem.cli.core.internal.PreferenceRegistry.PreferenceModel;
import com.ibm.team.filesystem.cli.core.internal.ScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.ITypedPreferenceRegistry;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.eclipse.osgi.util.NLS;






public class PreferenceListCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  Map<String, String> currentPreferences;
  Map<String, PreferenceRegistry.PreferenceModel> defaultPreferences;
  
  public PreferenceListCmd() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    options.addOption(CommonOptions.OPT_VERBOSE, Messages.PreferenceListCmd_VerboseHelp);
    return options;
  }
  
  public void run() throws FileSystemException
  {
    currentPreferences = ((ScmClientConfiguration)config).getPersistentPreferences()
      .getRegistryContents();
    
    defaultPreferences = PreferenceRegistry.getDefaultPreferences();
    List<String> validPreferences = new ArrayList();
    validPreferences.addAll(defaultPreferences.keySet());
    Collections.sort(validPreferences);
    
    String key = getKey(validPreferences);
    if (key != null) {
      validPreferences = Collections.singletonList(key);
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    IndentingPrintStream indent = out.indent();
    
    String nameText;
    if (config.getSubcommandCommandLine().hasOption(CommonOptions.OPT_VERBOSE))
    {
      int maxWidth = Messages.PreferenceListCmd_DefaultValue.length();
      int termWidth = SubcommandUtil.getTerminalWidth(config);
      
      nameText = StringUtil.pad(Messages.PreferenceListCmd_Name, maxWidth).toString();
      String descriptionText = StringUtil.pad(Messages.PreferenceListCmd_Description, 
        maxWidth).toString();
      
      for (String preferenceName : validPreferences) {
        PreferenceRegistry.PreferenceModel defaultPreference = (PreferenceRegistry.PreferenceModel)defaultPreferences.get(preferenceName);
        List<String> preferenceValueString = getPreferenceValueString(preferenceName);
        
        indent.println();
        
        indent.println(NLS.bind(Messages.PreferenceListCmd_SectionText, nameText, 
          preferenceName));
        
        String alignText = ": ";
        for (int index = 0; index < defaultPreference.getDescription().length; index++) {
          String desc = defaultPreference.getDescription()[index];
          Collection<String> lines = null;
          if (index == 0) {
            lines = StringUtil.wrapAndIndent(NLS.bind(
              Messages.PreferenceListCmd_SectionText, descriptionText, desc), 
              alignText, termWidth - out.getIndent());
          } else {
            int indentLen = descriptionText.length() + alignText.length() + 1;
            lines = StringUtil.wrapAndIndent(desc, termWidth - out.getIndent(), indentLen);
          }
          for (String line : lines) {
            indent.println(line);
          }
        }
        
        indent.println(NLS.bind(Messages.PreferenceListCmd_SectionText, 
          Messages.PreferenceListCmd_DefaultValue, preferenceValueString.get(1)));
        
        indent.println(NLS.bind(Messages.PreferenceListCmd_SectionText, 
          Messages.PreferenceListCmd_CurrentValue, preferenceValueString.get(0)));
      }
    } else {
      int maxWidth = 0;
      for (String preferenceName : validPreferences) {
        maxWidth = Math.max(maxWidth, preferenceName.length());
      }
      
      for (String preferenceName : validPreferences) {
        List<String> preferenceValueString = getPreferenceValueString(preferenceName);
        indent.println(NLS.bind(Messages.PreferenceListCmd_KeyValue, 
          StringUtil.pad(preferenceName, maxWidth), preferenceValueString.get(0)));
      }
    }
  }
  
  protected String getKey(List<String> validPreferences) throws CLIFileSystemClientException
  {
    return null;
  }
  






  private List<String> getPreferenceValueString(String preferenceName)
    throws FileSystemException
  {
    PreferenceRegistry.PreferenceModel defaultPreference = (PreferenceRegistry.PreferenceModel)defaultPreferences.get(preferenceName);
    String defaultPreferenceValue = defaultPreference.getDefaultValue();
    String preferenceValue = (String)currentPreferences.get(preferenceName);
    String preferenceUnits = defaultPreference.getUnits();
    

    if ((preferenceName.equalsIgnoreCase("trace.dir")) && (preferenceValue == null)) {
      defaultPreferenceValue = config.getConfigDirectory().getAbsolutePath();
    }
    
    if (preferenceValue == null) {
      if ((defaultPreferenceValue == null) || (defaultPreferenceValue.length() == 0)) {
        preferenceValue = defaultPreference.getDefaultDisplayValue();
      } else {
        preferenceValue = defaultPreferenceValue;
      }
    }
    
    List<String> preferenceValueString = new ArrayList();
    if ((preferenceUnits != null) && (preferenceUnits.trim().length() != 0)) {
      preferenceValueString.add(NLS.bind(Messages.PreferenceListCmd_Value_Units, 
        preferenceValue, preferenceUnits));
      preferenceValueString.add(NLS.bind(Messages.PreferenceListCmd_Value_Units, 
        defaultPreferenceValue, preferenceUnits));
    } else {
      preferenceValueString.add(preferenceValue);
      if ((defaultPreferenceValue == null) || (defaultPreferenceValue.length() == 0)) {
        preferenceValueString.add(defaultPreference.getDefaultDisplayValue());
      } else {
        preferenceValueString.add(defaultPreferenceValue);
      }
    }
    
    return preferenceValueString;
  }
}
