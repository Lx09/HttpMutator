package es.us.isa.httpmutator.core.converter;


import es.us.isa.httpmutator.core.model.StandardHttpResponse;

/**
 * Bidirectional converter interface for converting between
 * original response types and StandardHttpResponse
 */
public interface BidirectionalConverter<T> {

    /**
     * Convert from original response to StandardHttpResponse
     */
    StandardHttpResponse toStandardResponse(T originalResponse) throws ConversionException;

    /**
     * Convert from StandardHttpResponse back to original response type
     */
    T fromStandardResponse(StandardHttpResponse standardResponse) throws ConversionException;

    /**
     * Check if reverse conversion is supported for the given StandardHttpResponse
     */
    default boolean supportsReverseConversion(StandardHttpResponse standardResponse) {
        return true;
    }

    /**
     * Get the name of this converter for identification
     */
    String getName();

    /**
     * Check if this converter supports the given response type
     */
    boolean supports(Class<?> responseType);
}