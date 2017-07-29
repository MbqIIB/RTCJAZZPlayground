package com.ibm.team.filesystem.cli.client.internal.handler;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocalChange;
import com.ibm.team.filesystem.client.IRelativeLocation;
import com.ibm.team.filesystem.client.internal.utils.ConfigurationFacade;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;







public class UncheckedInChangesHandler
{
  private int uncheckedInChanges;
  private Map<ConfigurationFacade, Collection<ILocalChange>> map = new HashMap();
  
  public UncheckedInChangesHandler(int uncheckedInChanges) {
    this.uncheckedInChanges = uncheckedInChanges;
  }
  


  public int uncheckedInChanges(Map<ConfigurationFacade, Collection<ILocalChange>> changes)
  {
    for (Map.Entry<ConfigurationFacade, Collection<ILocalChange>> entry : changes.entrySet()) {
      Collection<ILocalChange> localChanges = (Collection)map.get(entry.getKey());
      if (localChanges == null) {
        localChanges = new ArrayList();
        map.put((ConfigurationFacade)entry.getKey(), localChanges);
      }
      localChanges.addAll((Collection)entry.getValue());
    }
    return uncheckedInChanges;
  }
  
  public void throwExceptions() throws FileSystemException {
    if ((uncheckedInChanges == 1) && 
      (!map.isEmpty())) {
      List<String> paths = new ArrayList();
      Iterator localIterator2; ILocalChange localChange; for (Iterator localIterator1 = map.entrySet().iterator(); localIterator1.hasNext(); 
          localIterator2.hasNext())
      {
        Map.Entry<ConfigurationFacade, Collection<ILocalChange>> entry = (Map.Entry)localIterator1.next();
        localIterator2 = ((Collection)entry.getValue()).iterator(); continue;localChange = (ILocalChange)localIterator2.next();
        paths.add(localChange.getPath().toString());
      }
      
      Collections.sort(paths);
      StringBuilder builder = new StringBuilder(Messages.UncheckedInChangesHandler_0);
      for (String path : paths) {
        builder.append("\n");
        builder.append(path);
      }
      throw StatusHelper.uncheckedInChanges(builder.toString());
    }
  }
}
