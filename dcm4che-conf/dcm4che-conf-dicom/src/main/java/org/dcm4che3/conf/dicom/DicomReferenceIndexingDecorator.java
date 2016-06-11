package org.dcm4che3.conf.dicom;

import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.api.Path;
import org.dcm4che3.conf.core.index.ConfIndexOutOfSyncException;
import org.dcm4che3.conf.core.index.ReferenceIndexingDecorator;
import org.dcm4che3.conf.core.util.PathPattern;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * Extension to shortcut DicomPath lookups through the reference index
 *
 * @author rawmahn
 */
public class DicomReferenceIndexingDecorator extends ReferenceIndexingDecorator {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(DicomReferenceIndexingDecorator.class);

    public DicomReferenceIndexingDecorator() {
    }

    public DicomReferenceIndexingDecorator(Configuration delegate, Map<String, Path> uuidToSimplePathCache) {
        super(delegate, uuidToSimplePathCache);
    }


    @Override
    public Iterator search(String liteXPathExpression) throws IllegalArgumentException, ConfigurationException {
        PathPattern.PathParser pp;

        pp = DicomPath.DeviceUUIDByAnyUUID.parseIfMatches(liteXPathExpression);
        if (pp != null) {
            return handleRefShortCut(pp, DicomPath.DeviceUUIDByAnyUUID, liteXPathExpression);
        }

        pp = DicomPath.DeviceNameByUUID.parseIfMatches(liteXPathExpression);
        if (pp != null) {
            return handleRefShortCut(pp, DicomPath.DeviceNameByUUID, liteXPathExpression);
        }

        pp = DicomPath.DeviceNameByAEUUID.parseIfMatches(liteXPathExpression);
        if (pp != null) {
            return handleRefShortCut(pp, DicomPath.DeviceNameByAEUUID, liteXPathExpression);
        }

        return super.search(liteXPathExpression);
    }

    private Iterator handleRefShortCut(PathPattern.PathParser pp, DicomPath pathType, String originalRequestPath) {

        String param;
        String suffix;
        switch (pathType) {
            case DeviceNameByUUID:
                param = "deviceUUID";
                suffix = "/dicomDeviceName";
                break;
            case DeviceUUIDByAnyUUID:
                param = "UUID";
                suffix = "/_.uuid";
                break;
            case DeviceNameByAEUUID:
                param = "aeUUID";
                suffix = "/dicomDeviceName";
                break;
            default:
                throw new IllegalArgumentException();

        }

        String uuid = pp.getParam(param);

        // resilience - double check
        // TODO: speed-up/cleanup ~ 8.2
        try {
            getNodeByUUID(null, uuid);
        } catch (ConfIndexOutOfSyncException e) {
            log.error("Config index out of sync!", e);
            return super.search(originalRequestPath);
        }

        Path path = getPathByUUIDFromIndex(uuid);

        if (path == null || path.getPathItems().size() == 0) {
            return Collections.emptyList().iterator();
        }

        if (path.getPathItems().size() < 3) {
            log.error("Unexpected path to device - wrong length: " + path);
            return Collections.emptyList().iterator();
        }

        path = path.subPath(0, 3);

        if (!validateDevicePath(path)) {
            log.error("Unexpected path to device:" + path);
            return Collections.emptyList().iterator();
        }

        return Collections.singletonList(getConfigurationNode(path.toSimpleEscapedXPath() + suffix, null)).iterator();
    }

    private boolean validateDevicePath(Path path) {

        return "dicomConfigurationRoot".equals(path.getPathItems().get(0))
                && "dicomDevicesRoot".equals(path.getPathItems().get(1))
                && (path.getPathItems().get(2) instanceof String);
    }
}
