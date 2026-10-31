package tv.lumo.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecureStreamUrlTest {

    @Test
    @DisplayName("seuls le schéma et le port changent")
    void keepsEverythingButSchemeAndPort() {
        assertThat(SecureStreamUrl.of("http://panel.example:8080/live/us%2Fer/p+w/1.m3u8", 8443))
                .isEqualTo("https://panel.example:8443/live/us%2Fer/p+w/1.m3u8");
        assertThat(SecureStreamUrl.of("http://panel.example/get?a=1&b=2", 8443))
                .isEqualTo("https://panel.example:8443/get?a=1&b=2");
    }

    @Test
    @DisplayName("le port 443 ne s'écrit pas")
    void omitsTheDefaultPort() {
        assertThat(SecureStreamUrl.of("http://panel.example:8080/movie/u/p/1.mp4", 443))
                .isEqualTo("https://panel.example/movie/u/p/1.mp4");
    }

    @Test
    @DisplayName("rien à proposer sans port annoncé")
    void nothingWithoutAPort() {
        assertThat(SecureStreamUrl.of("http://panel.example/live/u/p/1.m3u8", null)).isNull();
    }

    @Test
    @DisplayName("une adresse déjà https ou illisible reste sans alternative")
    void nothingWhenNotPlainHttp() {
        assertThat(SecureStreamUrl.of("https://panel.example/live/u/p/1.m3u8", 8443)).isNull();
        assertThat(SecureStreamUrl.of("http://panel example/live", 8443)).isNull();
    }
}
