package io.github.xianynomial.sfmfactorystudio.chronosfm;

/**
 * Implemented onto selected SFM structures by optional mixins.
 *
 * A missing interface means ChronoSFM cannot prove cache validity and must use
 * the original SFM discovery path.
 */
public interface ChronoRevisionSource {
    long chronosfm$getRevision();
}
