(() => {
  "use strict";

  const clamp = (value, minimum, maximum) => Math.min(maximum, Math.max(minimum, value));
  const assert = (condition, message) => {
    if (!condition) throw new Error(message);
  };
  const finite = (value, name) => {
    assert(Number.isFinite(value), `Responsive contract value is missing or invalid: ${name}`);
    return value;
  };

  function contractUrl() {
    const meta = document.querySelector('meta[name="nh01-responsive-contract-url"]');
    assert(meta?.content, "Responsive contract URL metadata is missing.");
    const root = document.querySelector('meta[name="nh01-preview-root"]');
    assert(root?.content, "NH01 preview root metadata is missing.");
    return new URL(meta.content, new URL(root.content, location.href));
  }

  async function loadContract() {
    const url = contractUrl();
    const response = await fetch(url, { cache: "no-store" });
    assert(response.ok, `Responsive contract load failed (${response.status}): ${url.pathname}`);
    const bytes = await response.arrayBuffer();
    const digest = await crypto.subtle.digest("SHA-256", bytes);
    const sha256 = Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, "0")).join("");
    const contract = JSON.parse(new TextDecoder().decode(bytes));
    const expectedId = document.querySelector('meta[name="nh01-responsive-contract-id"]')?.content;
    const expectedVersion = document.querySelector('meta[name="nh01-responsive-contract-version"]')?.content;
    assert(contract.contract_id === expectedId, `Responsive contract id mismatch: ${contract.contract_id}`);
    assert(contract.contract_version === expectedVersion, `Responsive contract version mismatch: ${contract.contract_version}`);
    assert(contract.status === "PROPOSED_FOR_CANONICAL_MAIN_REVIEW" || contract.status === "SPEC_FROZEN", `Unsupported responsive contract status: ${contract.status}`);
    assert(contract.responsive_model?.model === "CONSTRAINT_DRIVEN_SINGLE_POLICY", "Responsive model mismatch.");
    assert(contract.responsive_model?.viewport_keyed_layout_overrides === false, "Viewport-keyed layout overrides are forbidden.");
    assert(contract.preview_engine?.engine_id === "NH01_ADAPTIVE_V2_CONTRACT_PREVIEW_V1", "Responsive preview engine is missing or mismatched.");
    assert(contract.preview_engine?.coordinate_space?.runtime_evidence === false, "Browser preview must remain separate from Android Runtime evidence.");
    assert(contract.preview_engine?.parameters && contract.preview_engine?.role_bindings, "Responsive preview policy is incomplete.");
    const hero = contract.preview_engine.parameters.hero;
    assert(Number.isFinite(hero.minimum_readable_height_dp) && Number.isFinite(hero.maximum_readable_height_dp), "Responsive Hero readability bounds are missing or invalid.");
    assert(Number.isFinite(hero.short_screen_exception?.minimum_height_dp) && Number.isFinite(hero.short_screen_exception?.minimum_width_dp), "Responsive short-screen Hero bounds are missing or invalid.");
    return {
      contract,
      contractPath: contract.authority?.machine_contract || "design/pages/home/normal_home/responsive/NH01_RESPONSIVE_LAYOUT_V2.json",
      contractUrl: url.href,
      contractSha256: sha256,
      contractVersion: contract.contract_version
    };
  }

  function resolveInsets(params, defaults) {
    const raw = params.get("safeInsets");
    if (!raw) return { ...defaults };
    const values = raw.split(",").map(Number);
    assert(values.length === 4 && values.every(Number.isFinite), "safeInsets must be top,bottom,start,end numeric dp values.");
    assert(values.every(value => value >= 0), "Safe insets cannot be negative.");
    return { top: values[0], bottom: values[1], start: values[2], end: values[3] };
  }

  function interpolateCurve(points, x, xKey, yKey) {
    assert(Array.isArray(points) && points.length >= 2, `Responsive interpolation curve is incomplete: ${yKey}`);
    const ordered = [...points].sort((left, right) => left[xKey] - right[xKey]);
    const first = ordered[0];
    const last = ordered[ordered.length - 1];
    if (x <= first[xKey]) return first[yKey];
    if (x >= last[xKey]) return last[yKey];
    for (let index = 1; index < ordered.length; index += 1) {
      const right = ordered[index];
      const left = ordered[index - 1];
      if (x <= right[xKey]) {
        const progress = (x - left[xKey]) / (right[xKey] - left[xKey]);
        return left[yKey] + (right[yKey] - left[yKey]) * progress;
      }
    }
    throw new Error(`Unable to evaluate responsive interpolation curve: ${yKey}`);
  }

  function roleFor(contract, bindingKey) {
    const roleId = contract.preview_engine.role_bindings[bindingKey];
    const role = contract.typography.roles.find(item => item.id === roleId);
    assert(role, `Responsive typography role is not registered: ${bindingKey} → ${roleId}`);
    return role;
  }

  function responsiveSp(contract, bindingKey, safeHeight, viewportScale) {
    const role = roleFor(contract, bindingKey);
    assert(Number.isFinite(role.base_ratio) && Number.isFinite(role.min_sp) && Number.isFinite(role.max_sp), `Responsive typography role is not computable: ${role.id}`);
    return clamp(safeHeight * role.base_ratio, role.min_sp, role.max_sp) * viewportScale;
  }

  function computeAdaptive(input, contract) {
    const p = contract.preview_engine.parameters;
    const coordinate = contract.preview_engine.coordinate_space;
    const width = finite(input.widthDp, "logicalViewportWidthDp");
    const height = finite(input.heightDp, "logicalViewportHeightDp");
    const fontScale = finite(input.fontScale, "systemFontScale");
    const insets = input.safeInsetsDp;
    const density = finite(coordinate.logical_dp_to_css_px, "logical_dp_to_css_px");
    const safeWidth = width - insets.start - insets.end;
    const safeHeight = height - insets.top - insets.bottom;
    assert(safeWidth > 0 && safeHeight > 0, "Safe drawing area is empty.");

    const content = p.content;
    const maxContentWidth = finite(content.max_width_dp, "content.max_width_dp");
    const contentWidth = Math.min(safeWidth, maxContentWidth);
    const contentOffset = insets.start + Math.max(0, (safeWidth - contentWidth) / 2);
    const viewportScaleRule = p.viewport_scale;
    const viewportScale = clamp(
      Math.min(Math.min(safeWidth, maxContentWidth) / finite(viewportScaleRule.width_reference_dp, "viewport_scale.width_reference_dp"), safeHeight / finite(viewportScaleRule.height_reference_dp, "viewport_scale.height_reference_dp")),
      finite(viewportScaleRule.minimum, "viewport_scale.minimum"),
      finite(viewportScaleRule.maximum, "viewport_scale.maximum")
    );
    const roleSizesSp = {
      stat_value: responsiveSp(contract, "stat_value", safeHeight, viewportScale),
      stat_label: responsiveSp(contract, "stat_label", safeHeight, viewportScale),
      section_title: responsiveSp(contract, "section_title", safeHeight, viewportScale),
      section_action: responsiveSp(contract, "section_action", safeHeight, viewportScale),
      cta: responsiveSp(contract, "cta", safeHeight, viewportScale),
      species: responsiveSp(contract, "species", safeHeight, viewportScale),
      measurement: responsiveSp(contract, "measurement", safeHeight, viewportScale),
      metadata: responsiveSp(contract, "metadata", safeHeight, viewportScale)
    };
    const fontPressureRule = p.spacing.font_pressure;
    const shortPressureRule = p.spacing.short_height_pressure;
    const fontPressure = clamp((fontScale - fontPressureRule.base_font_scale) / fontPressureRule.range, 0, 1);
    const shortHeightPressure = clamp((shortPressureRule.reference_height_dp - safeHeight) / shortPressureRule.range_dp, 0, 1);
    const brandToStats = p.spacing.brand_to_stats;
    const statsToTitleRule = p.spacing.stats_to_title;
    const titleToCardRule = p.spacing.title_to_card;
    const cardToFooterRule = p.spacing.card_to_footer;
    let statsToTitle = Math.max(
      statsToTitleRule.minimum_gap_dp,
      interpolateCurve(statsToTitleRule.base_curve, safeHeight, "safe_height_dp", "value_dp") - statsToTitleRule.font_pressure_penalty_dp * fontPressure
    );
    const titleToCard = Math.max(
      titleToCardRule.minimum_gap_dp,
      interpolateCurve(titleToCardRule.base_curve, safeHeight, "safe_height_dp", "value_dp") - titleToCardRule.font_pressure_penalty_dp * fontPressure
    );
    let cardToFooter = Math.max(
      cardToFooterRule.minimum_gap_dp,
      clamp(safeHeight * cardToFooterRule.height_fraction, cardToFooterRule.base_minimum_dp, cardToFooterRule.base_maximum_dp)
        - cardToFooterRule.font_pressure_penalty_dp * fontPressure
        - cardToFooterRule.short_height_pressure_penalty_dp * shortHeightPressure
    );
    const footerCurve = p.spacing.footer.transition_curve;
    const largeBottomInset = insets.bottom >= p.spacing.footer.large_bottom_inset_threshold_dp;
    const footer = {
      ctaCameraGap: interpolateCurve(footerCurve, safeHeight, "safe_height_dp", "cta_camera_gap_dp"),
      topPadding: interpolateCurve(footerCurve, safeHeight, "safe_height_dp", "top_padding_dp"),
      bottomPadding: interpolateCurve(
        footerCurve,
        safeHeight,
        "safe_height_dp",
        largeBottomInset ? "bottom_padding_large_inset_dp" : "bottom_padding_small_inset_dp"
      )
    };
    const brandRules = p.brand_title;
    const avatarRules = p.avatar;
    const heroRules = p.hero;
    const neighborRules = p.neighbor;
    const cameraRules = p.camera;
    const cardRules = p.card_content;
    const topPadding = finite(content.top_padding_dp, "content.top_padding_dp");
    const brandWidth = clamp(
      safeWidth * brandRules.width_ratio_numerator / brandRules.width_ratio_denominator,
      brandRules.min_width_dp,
      brandRules.max_width_dp
    );
    const avatarSize = clamp(safeWidth * avatarRules.width_ratio, avatarRules.min_visual_dp, avatarRules.max_visual_dp);
    const cameraSize = clamp(
      Math.min(Math.min(safeWidth, maxContentWidth) * cameraRules.width_fraction_of_capped_safe_width, safeHeight * cameraRules.height_fraction_of_safe_height),
      cameraRules.minimum_visual_dp,
      cameraRules.maximum_visual_dp
    );
    const cameraTouchSize = Math.max(cameraRules.minimum_touch_dp, cameraSize);
    const topPaddingRule = content.top_horizontal_padding;
    const sectionPaddingRule = content.section_horizontal_padding;
    const topHorizontalPadding = clamp(safeWidth * topPaddingRule.ratio, topPaddingRule.min_dp, topPaddingRule.max_dp);
    const sectionHorizontalPadding = clamp(contentWidth * sectionPaddingRule.ratio, sectionPaddingRule.min_dp, sectionPaddingRule.max_dp);
    const brandStatsGap = Math.max(
      brandToStats.minimum_gap_dp,
      clamp(safeHeight * brandToStats.height_fraction, brandToStats.base_minimum_dp, brandToStats.base_maximum_dp)
        - brandToStats.font_pressure_penalty_dp * fontPressure
        - brandToStats.short_height_pressure_penalty_dp * shortHeightPressure
    );
    const textMetrics = {};
    for (const key of ["section_title", "section_action", "stat_value", "stat_label", "cta", "species", "measurement", "metadata"]) {
      const role = roleFor(contract, key);
      textMetrics[key] = { size_sp: roleSizesSp[key], size_css_px: roleSizesSp[key] * fontScale * density, line_height_ratio: role.line_height_ratio };
    }
    const sectionTitleLine = textMetrics.section_title;
    const sectionHeaderHeight = Math.max(
      finite(content.header_min_height_dp, "content.header_min_height_dp"),
      sectionTitleLine.size_css_px * sectionTitleLine.line_height_ratio + finite(content.section_vertical_padding_dp, "content.section_vertical_padding_dp") * 2
    );
    const headerHeight = Math.max(
      content.header_min_height_dp,
      brandRules.height_ratio_numerator / brandRules.height_ratio_denominator * brandWidth,
      avatarRules.minimum_touch_dp
    );
    const heroWidth = Math.min(contentWidth * heroRules.target_width_fraction, heroRules.maximum_width_dp);
    const heroHeight = Math.min(heroWidth * heroRules.aspect_ratio_height / heroRules.aspect_ratio_width, heroRules.maximum_height_dp);
    const edgePeek = clamp(
      contentWidth * neighborRules.edge_peek_width_fraction_of_track,
      neighborRules.minimum_edge_peek_dp,
      neighborRules.maximum_edge_peek_dp
    );
    const baseStatsToTitle = statsToTitle;
    const baseCardToFooter = cardToFooter;
    const bandAHeight = topPadding + headerHeight + brandStatsGap + content.stats_min_height_dp;
    const bandBHeight = sectionHeaderHeight + titleToCard + heroHeight;
    const bandCHeight = footer.topPadding + textMetrics.cta.size_sp * textMetrics.cta.line_height_ratio + footer.ctaCameraGap + cameraTouchSize + footer.bottomPadding;
    const logicalSurplus = Math.max(0, safeHeight - (bandAHeight + bandBHeight + bandCHeight + baseStatsToTitle + baseCardToFooter));
    statsToTitle = baseStatsToTitle + (2 * logicalSurplus) / 3;
    cardToFooter = baseCardToFooter + logicalSurplus / 3;
    const logicalAllocation = {
      safe_height_dp: safeHeight,
      band_heights_dp: { A: bandAHeight, B: bandBHeight, C: bandCHeight },
      gAB_base_dp: baseStatsToTitle,
      gBC_base_dp: baseCardToFooter,
      surplus_dp: logicalSurplus,
      final_a_to_b_dp: statsToTitle,
      final_b_to_c_dp: cardToFooter,
      internal_title_to_hero_dp: titleToCard,
      safe_insets_consumed_once: true
    };
    const cameraHorizontalRoom = contentWidth;
    return {
      variant: "ADAPTIVE_V2",
      contract_id: contract.contract_id,
      contract_version: contract.contract_version,
      contract_status: contract.status,
      contract_path: contract.authority?.machine_contract || "design/pages/home/normal_home/responsive/NH01_RESPONSIVE_LAYOUT_V2.json",
      contract_sha256: input.contractSha256,
      logical_viewport_dp: [width, height],
      safe_insets_dp: { ...insets },
      safe_width_dp: safeWidth,
      safe_height_dp: safeHeight,
      content_width_dp: contentWidth,
      content_left_dp: contentOffset,
      logical_dp_to_css_px: density,
      system_font_scale: fontScale,
      viewport_scale: viewportScale,
      font_pressure: fontPressure,
      short_height_pressure: shortHeightPressure,
      typography_base_sp: roleSizesSp,
      typography_sizes_dp: {
        stat_value: roleSizesSp.stat_value,
        stat_label: roleSizesSp.stat_label,
        section_title: roleSizesSp.section_title,
        section_action: roleSizesSp.section_action,
        cta: roleSizesSp.cta,
        species: roleSizesSp.species,
        measurement: roleSizesSp.measurement,
        metadata: roleSizesSp.metadata
      },
      typography_effective_css_px: Object.fromEntries(Object.entries(roleSizesSp).map(([key, value]) => [key, value * fontScale * density])),
      typography_line_height_ratios: Object.fromEntries(Object.entries(textMetrics).map(([key, value]) => [key, value.line_height_ratio])),
      brand_width_dp: brandWidth,
      avatar_visual_size_dp: avatarSize,
      avatar_touch_size_dp: avatarRules.minimum_touch_dp,
      camera_visual_size_dp: cameraSize,
      camera_touch_size_dp: cameraTouchSize,
      camera_horizontal_room_dp: cameraHorizontalRoom,
      top_padding_dp: topPadding,
      top_horizontal_padding_dp: topHorizontalPadding,
      section_horizontal_padding_dp: sectionHorizontalPadding,
      header_height_dp: headerHeight,
      section_header_height_dp: sectionHeaderHeight,
      stats_min_height_dp: content.stats_min_height_dp,
      stats_vertical_padding_dp: content.stats_vertical_padding_dp,
      stats_item_gap_dp: content.stats_item_gap_dp,
      brand_to_stats_gap_dp: brandStatsGap,
      stats_to_title_gap_target_dp: statsToTitle,
      title_to_card_gap_target_dp: titleToCard,
      card_to_footer_gap_dp: cardToFooter,
      gaps_dp: {
        brand_to_stats: brandStatsGap,
        stats_to_title: statsToTitle,
        title_to_card_target: titleToCard,
        card_to_footer: cardToFooter
      },
      logical_allocation: logicalAllocation,
      footer: {
        cta_camera_gap_dp: footer.ctaCameraGap,
        top_padding_dp: footer.topPadding,
        bottom_padding_dp: footer.bottomPadding
      },
      hero_target_size_dp: [heroWidth, heroHeight],
      hero_aspect_ratio: [heroRules.aspect_ratio_width, heroRules.aspect_ratio_height],
      hero_readable_height_range_dp: [heroRules.minimum_readable_height_dp, heroRules.maximum_readable_height_dp],
      neighbor_target_edge_peek_dp: edgePeek,
      neighbor_maximum_width_dp: heroWidth * neighborRules.maximum_width_fraction_of_hero,
      neighbor_height_fraction: neighborRules.height_fraction_of_hero,
      neighbor_top_offset_dp: neighborRules.top_offset_dp,
      neighbor_maximum_overlap_dp: neighborRules.maximum_overlap_dp,
      card_content: cardRules,
      composition: content,
      rendered_text_metrics: textMetrics,
      responsive_parameters: p
    };
  }

  window.NH01ResponsiveContractRuntime = {
    clamp,
    computeAdaptive,
    interpolateCurve,
    loadContract,
    resolveInsets
  };
})();
