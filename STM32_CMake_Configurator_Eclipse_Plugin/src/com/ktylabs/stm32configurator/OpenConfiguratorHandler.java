package com.ktylabs.stm32configurator;

import java.nio.file.Path;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

/** Opens the configurator for the selected Eclipse project, not the plugin project. */
public final class OpenConfiguratorHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        IProject selected = projectFromSelection(HandlerUtil.getCurrentSelection(event));
        IWorkbenchPage page = window.getActivePage();
        if (selected == null && page != null && page.getSelection() != null) {
            selected = projectFromSelection(page.getSelection());
        }
        // When the user invokes the menu while a source file is active, use its project.
        if (selected == null && page != null) {
            IEditorPart editor = page.getActiveEditor();
            if (editor != null) {
                IEditorInput input = editor.getEditorInput();
                IFile file = input.getAdapter(IFile.class);
                if (file != null) selected = file.getProject();
            }
        }
        Path project = null;
        if (selected != null && selected.isAccessible() && selected.getLocation() != null) {
            project = selected.getLocation().toFile().toPath();
        }
        // Eclipse projects may contain linked CMake folders whose physical files
        // are outside the selected project's filesystem location. Resolve via
        // workspace resources first, not only Files.walk(projectDirectory).
        Path selectedCmake = null;
        if (selected != null && selected.isAccessible()) {
            final Path[] result = new Path[1];
            try {
                selected.accept((IResourceVisitor) resource -> {
                    if (resource instanceof IFile && "CMakeLists.txt".equals(resource.getName())
                            && resource.getLocation() != null) {
                        Path candidate = resource.getLocation().toFile().toPath();
                        if (result[0] == null || candidate.getNameCount() < result[0].getNameCount())
                            result[0] = candidate;
                    }
                    return result[0] == null || resource.getType() != IResource.FILE;
                });
            } catch (CoreException ignored) { /* Filesystem fallback in dialog */ }
            selectedCmake = result[0];
        }
        new ConfiguratorDialog(window.getShell(), project, selectedCmake).open();
        return null;
    }

    private static IProject projectFromSelection(ISelection selection) {
        if (!(selection instanceof IStructuredSelection)) return null;
        Object item = ((IStructuredSelection) selection).getFirstElement();
        if (item instanceof IProject) return (IProject) item;
        IResource resource = null;
        if (item instanceof IResource) resource = (IResource) item;
        else if (item instanceof IAdaptable) {
            IAdaptable adaptable = (IAdaptable) item;
            resource = adaptable.getAdapter(IResource.class);
            if (resource == null) return adaptable.getAdapter(IProject.class);
        }
        return resource == null ? null : resource.getProject();
    }
}
