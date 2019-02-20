/********************************************************************************
 * Copyright (c) 2018 Contributors to the Gamma project
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
package hu.bme.mit.gamma.uppaal.composition.transformation.commandhandler;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil.EqualityHelper;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import hu.bme.mit.gamma.uppaal.composition.transformation.CompositeToUppaalTransformer;
import hu.bme.mit.gamma.uppaal.composition.transformation.ModelUnfolder;
import hu.bme.mit.gamma.uppaal.composition.transformation.CompositeToUppaalTransformer.Scheduler;
import hu.bme.mit.gamma.dialog.DialogUtil;
import hu.bme.mit.gamma.statechart.model.Component;
import hu.bme.mit.gamma.statechart.model.Package;
import hu.bme.mit.gamma.statechart.model.StatechartDefinition;
import hu.bme.mit.gamma.uppaal.serializer.UppaalModelSerializer;
import hu.bme.mit.gamma.uppaal.transformation.ModelValidator;
import hu.bme.mit.gamma.uppaal.transformation.traceability.G2UTrace;
import uppaal.NTA;

public class CommandHandler extends AbstractHandler {

	protected Logger logger = Logger.getLogger("GammaLogger");

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection sel = HandlerUtil.getActiveMenuSelection(event);
		try {
			if (sel instanceof IStructuredSelection) {
				IStructuredSelection selection = (IStructuredSelection) sel;
				if (selection.getFirstElement() != null) {
					if (selection.getFirstElement() instanceof IFile) {
						ResourceSet resSet = new ResourceSetImpl();
						logger.log(Level.INFO, "Resource set for Gamma flattening M2M transformation created: " + resSet);
						String fileURISubstring = null;
						IFile selectedFile = (IFile) selection.toList().get(0);
						fileURISubstring = ((IFile) selectedFile).getLocationURI().toString().substring(5);
						// Decoding so spaces do not stir trouble
						fileURISubstring = URI.decode(fileURISubstring);
						URI packageUri = URI.createPlatformResourceURI(selectedFile.getFullPath().toString(), true);
						// Loading the model
						Package gammaPackage = this.loadResource(resSet, packageUri);
						Component component = this.checkResourceSet(resSet);
						logger.log(Level.INFO,
								"Resource set content for Gamma flattening after loading the Package: " + resSet);
						run(gammaPackage, component, fileURISubstring);
						return null;
					}
				}
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			logger.log(Level.SEVERE, exception.getMessage());
			DialogUtil.showErrorWithStackTrace(exception.getMessage(), exception);
		}
		return null;
	}

	private Package loadResource(ResourceSet resSet, URI uri) throws IllegalArgumentException {
		Resource resource = resSet.getResource(uri, true);
		EObject elem = resource.getContents().get(0);
		if (!(elem instanceof Package)) {
			throw new IllegalArgumentException("There must be a single package in the selection: " + elem.getClass());
		}
		return (Package) elem;
	}

	private Component checkResourceSet(ResourceSet resSet) throws IllegalArgumentException {
		if (resSet.getResources().size() != 1) {
			throw new IllegalArgumentException("There must be one file in the selection.");
		}
		Resource resource = resSet.getResources().get(0);
		if (resource.getContents().get(0) instanceof Package) {
			Package gammaPackage = (Package) resource.getContents().get(0);
			List<Component> syncCompositeList = gammaPackage.getComponents().stream()
					.filter(it -> !(it instanceof StatechartDefinition)).collect(Collectors.toList());
			if (syncCompositeList.size() != 1) {
				throw new IllegalArgumentException("There must be exactly one composite or wrapper component in the selection.");
			}
			return syncCompositeList.get(0);
		}
		throw new IllegalArgumentException("There is no composite definition in the selection.");
	}

	public void run(Package gammaPackage, Component component, String fileURISubstring) throws IOException {
		String parentFolder = fileURISubstring.substring(0, fileURISubstring.lastIndexOf("/"));
		String fileName = fileURISubstring.substring(fileURISubstring.lastIndexOf("/") + 1);
		String fileNameWithoutExtenstion = fileName.substring(0, fileName.lastIndexOf("."));
		// Unfolding the given system
		SimpleEntry<Package, Component> packageWithTopComponent = new ModelUnfolder().unfold(gammaPackage);
		Component topComponent = packageWithTopComponent.getValue();
		// Saving the Package of the unfolded model
		String flattenedModelFileName = "." + fileNameWithoutExtenstion + ".gsm";
		normalSave(packageWithTopComponent.getKey(), parentFolder, flattenedModelFileName);
		// Reading the model from disk as this is the only way it works
		ResourceSet resourceSet = new ResourceSetImpl();
		logger.log(Level.INFO, "Resource set for flattened Gamma to UPPAAL transformation created: " + resourceSet);
		Resource resource = resourceSet
				.getResource(URI.createFileURI(parentFolder + File.separator + flattenedModelFileName), true);
		// Needed because reading from disk means it is another model now
		Component newTopComponent = getEquivalentComposite(resource, topComponent);
		// Checking the model whether it contains forbidden elements
		ModelValidator validator = new ModelValidator(resourceSet, newTopComponent);
		validator.checkModel();
		logger.log(Level.INFO, "Resource set content for flattened Gamma to UPPAAL transformation: " + resourceSet);
		CompositeToUppaalTransformer transformer = new CompositeToUppaalTransformer(resourceSet,
				newTopComponent, Scheduler.RANDOM, false); // newTopComponent
		SimpleEntry<NTA, G2UTrace> resultModels = transformer.execute();
		NTA nta = resultModels.getKey();
		// Saving the generated models
		normalSave(nta, parentFolder, "." + fileNameWithoutExtenstion + ".uppaal");
		normalSave(resultModels.getValue(), parentFolder, "." + fileNameWithoutExtenstion + ".g2u");
		// Serializing the NTA model to XML
		UppaalModelSerializer.saveToXML(nta, parentFolder, fileNameWithoutExtenstion + ".xml");
		UppaalModelSerializer.createStateReachabilityQueries(transformer.getTemplateLocationsMap(), "isStable", parentFolder,
				fileNameWithoutExtenstion + ".q");
		transformer.dispose();
		logger.log(Level.INFO, "The composite system transformation has been finished.");
	}

	/**
	 * Returns whether the given objects are equal with respect to ecore copies.
	 */
	private boolean helperEquals(EObject lhs, EObject rhs) {
		EqualityHelper helper = new EqualityHelper();
		return helper.equals(lhs, rhs);
	}

	/**
	 * Returns the CompositeDefinition from the resource that equals to the given composite.
	 */
	private Component getEquivalentComposite(Resource resource, Component component) {
		Package gammaPackage = (Package) resource.getContents().get(0);
		Component foundComponent = (Component) gammaPackage.getComponents().get(0);
		if (helperEquals(component, foundComponent)) {
			return foundComponent;
		}
		throw new IllegalArgumentException("No equivalent component!");
	}

	private void normalSave(EObject rootElem, String parentFolder, String fileName) throws IOException {
		ResourceSet resourceSet = new ResourceSetImpl();
		Resource saveResource = resourceSet
				.createResource(URI.createFileURI(URI.decode(parentFolder + File.separator + fileName)));
		saveResource.getContents().add(rootElem);
		saveResource.save(Collections.EMPTY_MAP);
	}

}
