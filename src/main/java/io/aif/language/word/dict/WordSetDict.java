package io.aif.language.word.dict;

import com.google.inject.Guice;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import io.aif.language.common.settings.ISettings;
import io.aif.language.common.settings.SettingsModule;
import io.aif.language.word.comparator.IGroupComparator;

class WordSetDict {

  private static final Logger LOGGER = Logger.getLogger(WordSetDict.class);

  private static final ISettings SETTINGS = Guice.createInjector(new SettingsModule()).getInstance(ISettings.class);

  private final IGroupComparator setComparator;

  private Map<String, Set<String>> tokensSetCache = new HashMap<>();

  private List<Set<String>> tokens = new ArrayList<>();

  private Map<String, AtomicLong> tokensCount = new HashMap<>();

  WordSetDict(IGroupComparator setComparator) {
    this.setComparator = setComparator;
  }

  public void mergeSet(final Set<String> set) {
    set.forEach(token -> {
      tokensCount.putIfAbsent(token, new AtomicLong());
      tokensCount.get(token).incrementAndGet();
    });

    final int tokensSize = tokens.size();
    final Optional<Set<String>> tokensSetOpt = set
        .stream()
        .filter(token -> getSet(token).isPresent())
        .map(token -> getSet(token).get())
        .findFirst();
    if (tokensSetOpt.isPresent()) {
      final Set<String> tokensSet = tokensSetOpt.get();
      tokensSet.addAll(set);
      set.forEach(token -> tokensSetCache.put(token, tokensSet));
      return;
    }
    for (int i = 0; i < tokens.size(); i++) {
      final Set<String> targetSet = tokens.get(i);
      if (setComparator.compare(targetSet, set) >
          SETTINGS.wordSetDictComparatorThreshold()) {
        targetSet.addAll(set);
        set.forEach(token -> tokensSetCache.put(token, targetSet));
        return;
      }
    }
    if (tokens.size() != tokensSize) {
      mergeSet(set);
    } else {
      tokens.add(set);
      set.forEach(token -> tokensSetCache.put(token, set));
    }
    LOGGER.debug(String.format("words count is: %d", tokens.size()));
  }

  public List<Set<String>> getTokens() {
    return tokens;
  }

  public Long getCount(final Set<String> set) {
    return set.stream().mapToLong(token -> tokensCount.get(token).get()).sum();
  }

  private Optional<Set<String>> getSet(final String token) {
    if (!tokensSetCache.keySet().contains(token)) return Optional.empty();
    return Optional.of(tokensSetCache.get(token));
  }

}
