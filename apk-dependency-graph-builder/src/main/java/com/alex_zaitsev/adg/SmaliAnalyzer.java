package com.alex_zaitsev.adg;

import static com.alex_zaitsev.adg.util.CodeUtils.getAnonymousNearestOuter;
import static com.alex_zaitsev.adg.util.CodeUtils.getClassSimpleName;
import static com.alex_zaitsev.adg.util.CodeUtils.getEndGenericIndex;
import static com.alex_zaitsev.adg.util.CodeUtils.getOuterClass;
import static com.alex_zaitsev.adg.util.CodeUtils.isClassAnonymous;
import static com.alex_zaitsev.adg.util.CodeUtils.isClassGenerated;
import static com.alex_zaitsev.adg.util.CodeUtils.isClassInner;
import static com.alex_zaitsev.adg.util.CodeUtils.isInstantRunEnabled;
import static com.alex_zaitsev.adg.util.CodeUtils.isSmaliFile;

import com.alex_zaitsev.adg.filter.Filter;
import com.alex_zaitsev.adg.io.Filters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SmaliAnalyzer {

	private String projectPath;
	private Filters filters;
	private Filter<String> pathFilter;
	private Filter<String> classFilter;

	public SmaliAnalyzer(String projectPath,
						 Filters filters,
						 Filter<String> pathFilter,
						 Filter<String> classFilter) {
		this.projectPath = projectPath;
		this.filters = filters;
		this.pathFilter = pathFilter;
		this.classFilter = classFilter;
	}

	private Map<String, Map<String, Integer>> dependencies = new HashMap<>();

	public Map<String, Map<String, Integer>> getDependencies() {
		if (filters == null || filters.isProcessingInner()) {
			return dependencies;
		}
		return getFilteredDependencies();
	}

	public boolean run() {
		System.out.println("Analyzing dependencies...");

		File projectDir = new File(projectPath);
		if (projectDir.exists()) {
			if (isInstantRunEnabled(projectPath)) {
				System.err.println("Enabled Instant Run feature detected. " +
						"We cannot decompile it. Please, disable Instant Run and rebuild your app.");
			} else {
				traverseSmaliCodeDir(projectDir);
				return true;
			}
		} else {
			System.err.println(projectDir + " does not exist!");
		}
		return false;
	}
	
	private void traverseSmaliCodeDir(File dir) {
		File[] listOfFiles = dir.listFiles();
		for (int i = 0; i < listOfFiles.length; i++) {
			File currentFile = listOfFiles[i];
			if (isSmaliFile(currentFile)) {
				if (isPathFilterOk(currentFile)) {
					processSmaliFile(currentFile);
				}
			} else if (currentFile.isDirectory()) {
				traverseSmaliCodeDir(currentFile);
			}
		}
	}

	private boolean isPathFilterOk(File file) {
		return isPathFilterOk(file.getAbsolutePath());
	}

	private boolean isPathFilterOk(String filePath) {
		return pathFilter == null || pathFilter.filter(filePath);
	}

	private boolean isClassFilterOk(String className) {
		return classFilter == null || classFilter.filter(className);
	}

	private void processSmaliFile(File file) {
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {

			String fileName = file.getName().substring(0, file.getName().lastIndexOf("."));

			if (isClassAnonymous(fileName)) {
				fileName = getAnonymousNearestOuter(fileName);
			}

			if (!isClassFilterOk(fileName)) {
				return;
			}

			String dir = projectPath;
			String filepath = file.getPath();
			int direct = filepath.length() - ".smali".length();
			String thisClassName = filepath.substring(dir.length() + 1, direct).replace("/", ".");
			Set<String> classNames = new HashSet<>();
			HashMap<String, Integer> dependencyNames = new HashMap<>();

			for (String line; (line = br.readLine()) != null; ) {
				try {
					classNames.clear();

					parseAndAddClassNames(classNames, line);

					// filtering
					for (String fullClassName : classNames) {
						if (fullClassName != null && isPathFilterOk(fullClassName)) {
							String simpleClassName = getClassSimpleName(fullClassName);
							if (isClassFilterOk(simpleClassName) && isClassOk(simpleClassName, fileName) && !thisClassName.equals(simpleClassName)) {
								dependencyNames.merge(simpleClassName, 1, Integer::sum);
							}
						}
					}
				} catch (Exception e) {
					System.err.println("Error '" + e.getMessage() + "' occured.");
				}
			}

			// inner/nested class always depends on the outer class
			if (isClassInner(fileName)) {
				dependencyNames.merge(getOuterClass(fileName), 1, Integer::sum);
			}

			if (!dependencyNames.isEmpty()) {
				addDependencies(thisClassName, dependencyNames);
			}
		} catch (FileNotFoundException e) {
			System.err.println("Cannot found " + file.getAbsolutePath());
		} catch (IOException e) {
			System.err.println("Cannot read " + file.getAbsolutePath());
		}
	}
	
	/**
	 * The last filter. Do not show anonymous classes (their dependencies belongs to outer class), 
	 * generated classes, avoid circular dependencies
	 * @param simpleClassName class name to inspect
	 * @param fileName full class name
	 * @return true if class is good with these conditions
	 */
	private boolean isClassOk(String simpleClassName, String fileName) {
		return !isClassAnonymous(simpleClassName) && !isClassGenerated(simpleClassName)
				&& !fileName.equals(simpleClassName);
	}
	
	private void parseAndAddClassNames(Set<String> classNames, String line) {
		int index = line.indexOf("L");
		while (index != -1) {
			int colonIndex = line.indexOf(";", index);
			if (colonIndex == -1) {
				break;
			}

			String className = line.substring(index + 1, colonIndex);
			if (className.matches("[\\w\\d/$<>]*")) {
				int startGenericIndex = className.indexOf("<");
				if (startGenericIndex != -1 && className.charAt(startGenericIndex + 1) == 'L') {
					// generic
					int startGenericInLineIndex = index + startGenericIndex + 1; // index of "<" in the original string
					int endGenericInLineIndex = getEndGenericIndex(line, startGenericInLineIndex);
					String generic = line.substring(startGenericInLineIndex + 1, endGenericInLineIndex);
					parseAndAddClassNames(classNames, generic);
					index = line.indexOf("L", endGenericInLineIndex);
					className = className.substring(0, startGenericIndex);
				} else {
					index = line.indexOf("L", colonIndex);
				}
			} else {
				index = line.indexOf("L", index + 1);
				continue;
			}

			classNames.add(className);
		}
	}

	private void addDependencies(String className, HashMap<String, Integer> dependenciesList) {
		Map<String, Integer> depList = dependencies.get(className);
		if (depList == null) {
			// add this class and its dependencies
			dependencies.put(className, dependenciesList);
		} else {
			// if this class is already added - update its dependencies
			for (String dep : dependenciesList.keySet()) {
				depList.merge(dep, dependenciesList.get(dep), Integer::sum);
			}
		}
	}

	private Map<String, Map<String, Integer>> getFilteredDependencies() {
		Map<String, Map<String, Integer>> filteredDependencies = new HashMap<>();
		for (String key : dependencies.keySet()) {
			if (!key.contains("$")) {
				HashMap<String, Integer> dependencySet = new HashMap<>();
				for (String dependency : dependencies.get(key).keySet()) {
					if (!dependency.contains("$")) {
						dependencySet.put(dependency, dependencies.get(key).get(dependency));
					}
				}
				if (dependencySet.size() > 0) {
					filteredDependencies.put(key, dependencySet);
				}
			}
		}
		return filteredDependencies;
	}
}
