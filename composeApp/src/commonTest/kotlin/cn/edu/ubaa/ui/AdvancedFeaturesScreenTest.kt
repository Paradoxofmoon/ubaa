package cn.edu.ubaa.ui

import cn.edu.ubaa.ui.icons.MaterialRoundedIcons
import cn.edu.ubaa.ui.screens.menu.advancedFeatureItems
import kotlin.test.Test
import kotlin.test.assertFalse

class AdvancedFeaturesScreenTest {
  @Test
  fun `advanced features do not include signin entry`() {
    assertFalse(advancedFeatureItems(MaterialRoundedIcons.Set).any { it.id == "signin" })
  }
}
