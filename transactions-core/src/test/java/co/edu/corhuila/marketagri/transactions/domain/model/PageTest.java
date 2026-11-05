package co.edu.corhuila.marketagri.transactions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** D-C24: a bounded page (Anexo C, "página acotada"); totalPages = ceil(total / limit), 0 when empty. */
class PageTest {

    @Test
    void offsetSkipsTheEarlierPages() {
        assertThat(new PageRequest(3, 20).offset()).isEqualTo(40L);
    }

    @Test
    void limitOutsideOneToAHundredIsRefused() {
        assertThatThrownBy(() -> new PageRequest(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageRequest(1, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageBelowOneIsRefused() {
        assertThatThrownBy(() -> new PageRequest(0, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalPagesRoundsUp() {
        assertThat(new Page<>(List.of("a"), new PageRequest(1, 20), 41).totalPages()).isEqualTo(3);
    }

    @Test
    void noItemsIsZeroPages() {
        assertThat(new Page<>(List.of(), new PageRequest(1, 20), 0).totalPages()).isZero();
    }
}
