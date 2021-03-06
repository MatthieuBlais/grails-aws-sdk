package grails.plugin.awssdk.s3

import com.amazonaws.AmazonClientException
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.S3ObjectSummary
import grails.test.mixin.TestFor
import spock.lang.Specification

@TestFor(AmazonS3Service)
class AmazonS3ServiceSpec extends Specification {

    static String BUCKET_NAME = 'bucket'

    static doWithConfig(conf) {
        conf.grails.plugin.awssdk.s3.bucket = BUCKET_NAME
        conf.grails.plugin.awssdk.s3.region = 'eu-west-1'
    }

    def setup() {
        // Mock collaborator
        service.client = Mock(AmazonS3Client)
        service.init(BUCKET_NAME)
    }

    /**
     * Tests for deleteFile(String key)
     */
    void "Deleting a file "() {
        when:
        boolean deleted = service.deleteFile('key')

        then:
        deleted
        1 * service.client.deleteObject(BUCKET_NAME, 'key')
    }

    void "Deleting a file with service exception"() {
        when:
        boolean deleted = service.deleteFile('key')

        then:
        !deleted
        1 * service.client.deleteObject(_, _) >> {
            throw new AmazonS3Exception('some exception')
        }
    }

    void "Deleting a file with client exception"() {
        when:
        boolean deleted = service.deleteFile('key')

        then:
        !deleted
        1 * service.client.deleteObject(_, _) >> {
            throw new AmazonClientException('some exception')
        }
    }

    /**
     * Tests for deleteFiles(String prefix)
     */
    void "Deleting files"() {
        when:
        boolean deleted = service.deleteFiles('dir/subdir/*')

        then:
        deleted
        1 * service.client.listObjects(BUCKET_NAME, 'dir/subdir/*') >> {
            def objectListing = [objectSummaries: []]
            3.times {
                S3ObjectSummary summary = new S3ObjectSummary()
                summary.setKey("key$it")
                objectListing.objectSummaries << summary
            }
            objectListing as ObjectListing
        }
        3 * service.client.deleteObject(BUCKET_NAME, _)
    }

    void "Deleting files with invalid prefix"() {
        when:
        boolean deleted = service.deleteFiles('prefix')

        then:
        thrown AssertionError
        !deleted
        0 * service.client
    }

    void "Deleting files with service exception"() {
        when:
        boolean deleted = service.deleteFiles('dir/subdir/*')

        then:
        !deleted
        1 * service.client.listObjects(_, _) >> {
            throw new AmazonS3Exception('some exception')
        }
    }

    void "Deleting files with client exception"() {
        when:
        boolean deleted = service.deleteFiles('dir/subdir/*')

        then:
        !deleted
        1 * service.client.listObjects(_, _) >> {
            throw new AmazonClientException('some exception')
        }
    }

    /**
     * Tests for exists(String prefix)
     */
    void "Checking if a file exists"() {
        when:
        boolean exists = service.exists('key')

        then:
        exists
        1 * service.client.listObjects(BUCKET_NAME, 'key') >> {
            S3ObjectSummary summary = new S3ObjectSummary()
            summary.setKey('key')
            [objectSummaries: [summary]] as ObjectListing
        }
    }

    void "Checking if a file does not exists"() {
        when:
        boolean exists = service.exists('key')

        then:
        !exists
        1 * service.client.listObjects(BUCKET_NAME, 'key') >> {
            [] as ObjectListing
        }
    }

    void "Checking if a file exists with invalid key parameter"() {
        when:
        boolean exists = service.exists('')

        then:
        !exists
        0 * service.client
    }

    void "Checking if a file exists with service exception"() {
        when:
        boolean exists = service.exists('prefix')

        then:
        !exists
            1 * service.client.listObjects(_, _) >> {
                throw new AmazonS3Exception('some exception')
            }
    }

    void "Checking if a file exists with client exception"() {
        when:
        boolean exists = service.exists('prefix')

        then:
        !exists
        1 * service.client.listObjects(BUCKET_NAME, _) >> {
            throw new AmazonClientException('some exception')
        }
    }

    /**
     * Tests for generatePresignedUrl(String key, Date expiration)
     */
    void "Generating presigned url"() {
        when:
        String presignedUrl = service.generatePresignedUrl('key', new Date() + 1)

        then:
        presignedUrl == 'http://some.domaine.com/some/path'
        1 * service.client.generatePresignedUrl(BUCKET_NAME, 'key', _) >> new URL('http://some.domaine.com/some/path')
    }

    /**
     * Tests for storeFile(Object input, String type, String filePrefix, String fileExtension, String fileSuffix = '', CannedAccessControlList cannedAcl = CannedAccessControlList.PublicRead)
     */
    InputStream mockInputStream() {
        new InputStream() {
            @Override
            int read() throws IOException {
                return 0
            }
        }
    }

    void "Storing file"() {
        given:
        File file = Mock(File)
        String path = "filePrefix.txt"

        when:
        String url = service.storeFile(path, file)

        then:
        url == "https://s3-eu-west-1.amazonaws.com/${BUCKET_NAME}/${path}"
        1 * service.client.putObject(_)
    }

    void "Storing input"() {
        given:
        InputStream input = mockInputStream()
        String path = "filePrefix.txt"

        when:
        ObjectMetadata metadata = service.buildMetadataFromType('file', 'txt')
        String url = service.storeInputStream(path, input, metadata)

        then:
        url == "https://s3-eu-west-1.amazonaws.com/${BUCKET_NAME}/${path}"
        1 * service.client.putObject(BUCKET_NAME, path, input, _)
    }

    void "Storing pdf input with private ACL"() {
        given:
        InputStream input = mockInputStream()
        String path = "filePrefix.fileSuffix.pdf"

        when:
        ObjectMetadata metadata = service.buildMetadataFromType('pdf', 'pdf')
        String url = service.storeInputStream(path, input, metadata)

        then:
        url == "https://s3-eu-west-1.amazonaws.com/${BUCKET_NAME}/${path}"
        1 * service.client.putObject(BUCKET_NAME, path, input, _)
    }

    void "Storing image input"() {
        given:
        InputStream input = mockInputStream()
        String path = "filePrefix.fileSuffix.jpg"

        when:
        ObjectMetadata metadata = service.buildMetadataFromType('image', 'jpg')
        String url = service.storeInputStream(path, input, metadata)

        then:
        url == "https://s3-eu-west-1.amazonaws.com/${BUCKET_NAME}/${path}"
        1 * service.client.putObject(BUCKET_NAME, path, input, _)
    }

    void "Storing flash input"() {
        given:
        InputStream input = mockInputStream()
        String path = "filePrefix.fileSuffix.swf"

        when:
        ObjectMetadata metadata = service.buildMetadataFromType('flash', 'swf')
        String url = service.storeInputStream(path, input, metadata)

        then:
        url == "https://s3-eu-west-1.amazonaws.com/${BUCKET_NAME}/${path}"
        1 * service.client.putObject(BUCKET_NAME, path, input, _)
    }

    void "Storing input with service exception"() {
        given:
        InputStream input = mockInputStream()

        when:
        String url = service.storeInputStream('somePath', input, new ObjectMetadata())

        then:
        !url
        1 * service.client.putObject(BUCKET_NAME, _, _, _) >> {
            throw new AmazonS3Exception('some exception')
        }
    }

    void "Storing input with client exception"() {
        given:
        InputStream input = mockInputStream()

        when:
        String url = service.storeInputStream('somePath', input, new ObjectMetadata())

        then:
        !url
        1 * service.client.putObject(BUCKET_NAME, _, _, _) >> {
            throw new AmazonClientException('some exception')
        }
    }

}