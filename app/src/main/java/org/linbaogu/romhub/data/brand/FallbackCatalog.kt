package org.linbaogu.romhub.data.brand

/**
 * 内置机型兜底表。
 *
 * 正常情况下机型列表来自 [OpusIndex]（联网拉 opusrom 的静态清单，1159 机型）。
 * 但那个站不稳：请求头少一个 `Referer` 就403，被判定爆破还会**软封 IP 6 小时**。
 * 所以 App 里另带一份主流机型表，opusrom 拉不到时至少还能正常显示和下载。
 *
 * 表里存的是**官方 OTA 接口真正认的型号**，不是营销名：
 *  - OriginOS：`model` = `PD2415` 这种软件型号（`public_model` 形如 `V2418A` 在接口里另传）
 *  - ColorOS：`model` = `PJZ110` 这种，以 `_(` 结尾的才是完整版本号
 *
 * 一个 `model` 经常对应多个机型名（如 `PD2318` 同时是 G2 / Y36 / Y36i / Y36m / Y36s），
 * 因为它们共用同一套固件，所以这里把机型名合并成列表展示。
 */
data class FallbackModel(
    /** 官方接口的 model 号，如 `PD2415` / `PJZ110`。 */
    val model: String,
    /** 系列名，用于分组。 */
    val series: String,
    /** 共用这套固件的机型名（一个 model 可能有多个）。 */
    val names: List<String>,
    /** 已知的一个真实版本号，作为官方接口的 `swVer` 入参。 */
    val sampleVersion: String,
) {
    val displayName: String get() = names.joinToString(" / ")
}

object FallbackCatalog {

    // ------------------------------------------------------------- OriginOS（vivo）

    /** vivo 主力机型。2026-10-04 重建：型号与版本号直接取自 opusrom 在线清单的
     *  全部 109 款 vivo 机型（按 PD 号去重合并成 73 条），
     *  再按 GitHub `zc57534/MobileModels` 国行型号表补上摘要里没有的新机
     *  （S30 / X Fold5 / Y300+ / Pad5 等 12 条），合计 85 条。 */
    val vivo: List<FallbackModel> = listOf(
        FallbackModel("PD2456", "Y", listOf("Y300 Pro+"), "15.0.7.13.W10.V000L1"),
        FallbackModel("PD2454", "X", listOf("X200 Ultra"), "15.0.10.24.W10.V000L1"),
        FallbackModel("PD2445D", "Y", listOf("Y300t"), "15.0.10.1.W10.V000L1"),
        FallbackModel("PD2444", "Y", listOf("Y300i"), "15.0.11.6.W10.V000L1"),
        FallbackModel("PD2442", "Y", listOf("Y37c"), "14.0.10.8.W10.V000L1"),
        FallbackModel("PD2435", "Y", listOf("Y300"), "15.0.10.2.W10.V000L1"),
        FallbackModel("PD2430", "S", listOf("S20 Pro"), "15.0.10.4.W10.V000L1"),
        FallbackModel("DPD2429", "其他", listOf("vivo Pad5 Pro"), "15.0.12.18.W10.V000L1"),
        FallbackModel("PD2429", "S", listOf("S20"), "15.0.10.2.W10.V000L1"),
        FallbackModel("DPD2424", "其他", listOf("vivo Pad SE"), "15.0.10.0.W10.V000L1"),
        FallbackModel("PD2415", "X", listOf("X200", "X200 Pro mini", "X200s"), "15.0.18.35.W10.V000L1"),
        FallbackModel("PD2410", "Y", listOf("Y300 GT", "Y300 Pro"), "14.0.10.2.W10.V000L1"),
        FallbackModel("PD2405", "X", listOf("X200 Pro", "X200 Pro 卫星通信版"), "15.0.14.5.W10.V000L1"),
        FallbackModel("PD2366", "X", listOf("X100 Ultra"), "14.0.10.20.W10.V000L1"),
        FallbackModel("PD2364", "S", listOf("S19"), "14.0.10.0.W10.V000L1"),
        FallbackModel("PD2362", "S", listOf("S19 Pro"), "14.0.10.3.W10.V000L1"),
        FallbackModel("PD2361", "Y", listOf("Y200 GT", "Y200 Pro 企业定制版"), "14.0.10.0.W10.V000L1"),
        FallbackModel("PD2357", "Y", listOf("Y36c", "Y37", "Y37m"), "14.0.10.5.W10.V000L1"),
        FallbackModel("PD2354", "Y", listOf("Y100+", "Y200+", "Y200i", "Y37 Pro"), "14.0.13.7.W10.V000L1"),
        FallbackModel("PD2353", "Y", listOf("Y200t"), "14.0.10.1.W10.V000L1"),
        FallbackModel("DPD2350", "其他", listOf("Pad2 Pro"), "14.0.8.46.W10.V000L1"),
        FallbackModel("DPD2345", "其他", listOf("vivo Pad3"), "14.0.11.12.W10.V000L1"),
        FallbackModel("PD2344", "S", listOf("S18 Pro"), "14.0.10.3.W10.V000L1"),
        FallbackModel("PD2343", "Y", listOf("Y200"), "14.0.10.0.W10.V000L1"),
        FallbackModel("PD2337", "X", listOf("X Fold3 Pro"), "14.0.11.4.W10.V000L1"),
        FallbackModel("PD2334", "S", listOf("S18e"), "14.0.11.1.W10.V000L1"),
        FallbackModel("DPD2329", "其他", listOf("Pad3 Pro", "vivo Pad3 Pro"), "14.0.8.46.W10.V000L1"),
        FallbackModel("PD2327", "Y", listOf("Y36t"), "14.0.10.1.W10.V000L1"),
        FallbackModel("PD2324", "X", listOf("X100 Pro", "X100s Pro"), "14.0.14.6.W10.V000L1"),
        FallbackModel("PD2323", "S", listOf("S18"), "14.0.10.12.W10.V000L1"),
        FallbackModel("PD2318", "其他", listOf("G2", "Y36s"), "13.0.11.13.W10.V000L1"),
        FallbackModel("PD2318K", "Y", listOf("Y36", "Y36i", "Y36m"), "13.0.9.5.W10.V000L1"),
        FallbackModel("PD2317", "Y", listOf("Y12"), "13.0.10.0.W10.V000L1"),
        FallbackModel("PD2314D", "Y", listOf("Y100t"), "13.0.10.0.W10.V000L1"),
        FallbackModel("PD2313", "Y", listOf("Y100", "Y78s+", "Y79+"), "13.0.10.6.W10.V000L1"),
        FallbackModel("PD2312E", "Y", listOf("Y100i长续航版", "Y78t"), "13.0.10.0.W10.V000L1"),
        FallbackModel("PD2309", "X", listOf("X100", "X100s"), "14.0.21.57.W10.V000L1"),
        FallbackModel("DPD2305", "其他", listOf("vivo Pad Air"), "13.0.10.12.W10.V000L1"),
        FallbackModel("PD2303", "X", listOf("X Fold3"), "14.0.14.2.W10.V000L1"),
        FallbackModel("PD2285", "S", listOf("S17e"), "13.0.11.3.W10.V000L1"),
        FallbackModel("PD2284", "S", listOf("S17 Pro"), "13.0.10.1.W10.V000L1"),
        FallbackModel("PD2283", "S", listOf("S17"), "13.0.10.0.W10.V000L1"),
        FallbackModel("PD2282", "S", listOf("S17t"), "13.0.10.14.W10.V000L1"),
        FallbackModel("PD2279", "Y", listOf("Y35+", "Y35m+"), "13.0.10.2.W10.V000L1"),
        FallbackModel("PD2279J", "Y", listOf("Y100i", "Y55t", "Y78 (t1)", "Y78 (t1版)", "Y78m (t1)", "Y78m (t1版)", "Y79 5G"), "13.0.9.0.W10.V000L1"),
        FallbackModel("PD2278", "Y", listOf("Y77t", "Y78", "Y78m"), "13.0.18.2.W10.V000L1"),
        FallbackModel("PD2271", "Y", listOf("Y78+", "Y78+ (t1版)"), "13.0.12.0.W10.V000L1"),
        FallbackModel("PD2266", "X", listOf("X Fold2"), "13.0.11.0.W10.V000L1"),
        FallbackModel("PD2256", "X", listOf("X Flip"), "13.0.15.16.W10.V000L1"),
        FallbackModel("PD2245", "S", listOf("S16 Pro"), "13.0.10.4.W10.V000L1"),
        FallbackModel("PD2244", "S", listOf("S16"), "13.0.12.11.W10.V000L1"),
        FallbackModel("PD2242", "X", listOf("X90 Pro"), "13.0.11.18.W10.V000L1"),
        FallbackModel("PD2241", "X", listOf("X90", "X90s"), "13.0.22.15.W10.V000L1"),
        FallbackModel("PD2239", "S", listOf("S16e"), "11.0.5.20.W10.V000L1"),
        FallbackModel("PD2236", "Y", listOf("Y11", "Y33t"), "12.0.7.12.W10.V000L1"),
        FallbackModel("PD2230E", "Y", listOf("Y35", "Y35m", "Y53t"), "13.0.10.11.W10.V000L1"),
        FallbackModel("PD2229", "X", listOf("X Fold+"), "12.1.7.10.W10.V000L1"),
        FallbackModel("PD2227", "X", listOf("X90 Pro+"), "13.0.13.2.W10.V000L1"),
        FallbackModel("DPD2221", "其他", listOf("vivo Pad2"), "13.0.11.11.W10.V000L1"),
        FallbackModel("PD2219", "Y", listOf("Y77 Pro"), "12.0.10.13.W10.V000L1"),
        FallbackModel("PD2207", "S", listOf("S15 Pro"), "12.0.11.14.W10.V000L1"),
        FallbackModel("PD2203", "S", listOf("S15"), "12.0.10.2.W10.V000L1"),
        FallbackModel("PD2199G", "其他", listOf("T2"), "12.0.11.6.W10.V000L1"),
        FallbackModel("PD2190", "S", listOf("S15e"), "11.0.10.0.W10.V000L1"),
        FallbackModel("PD2188", "其他", listOf("T2x"), "12.0.10.1.W10.V000L1"),
        FallbackModel("PD2186", "X", listOf("X80 Pro 天玑9000版"), "12.0.14.14.W10.V000L1"),
        FallbackModel("PD2185", "X", listOf("X80 Pro"), "12.0.10.5.W10.V000L1"),
        FallbackModel("PD2183", "X", listOf("X80"), "12.0.12.1.W10.V000L1"),
        FallbackModel("PD2180D", "Y", listOf("Y10 (t2版)", "Y32t 8GB+256GB版"), "11.0.10.0.W10.V000L1"),
        FallbackModel("PD2178", "X", listOf("X Fold"), "12.0.12.12.W10.V000L1"),
        FallbackModel("PD2170", "其他", listOf("NEX 10 Pro", "X Note"), "12.0.10.7.W10.V000L1"),
        FallbackModel("PD2168", "Y", listOf("Y10 (t1版)", "Y32t"), "11.0.13.1.W10.V000L1"),
        FallbackModel("PD2158", "Y", listOf("Y32"), "11.0.1.0.W50.V000L1"),
        FallbackModel("PD2464", "S", listOf("vivo S30"), "15.0.10.8.W10.V000L1"),
        FallbackModel("PD2465", "S", listOf("vivo S30 Pro mini"), "15.0.10.10.W10.V000L1"),
        FallbackModel("PD2436", "X", listOf("vivo X Fold5"), "15.0.10.20.W10.V000L1"),
        FallbackModel("PD2359", "X", listOf("vivo X100s"), "14.0.10.30.W10.V000L1"),
        FallbackModel("PD2419", "X", listOf("vivo X200 Pro mini"), "15.0.11.5.W10.V000L1"),
        FallbackModel("PD2458", "X", listOf("vivo X200s"), "15.0.10.20.W10.V000L1"),
        FallbackModel("PD2435", "Y", listOf("vivo Y300c"), "15.0.10.2.W10.V000L1"),
        FallbackModel("PD2445E", "Y", listOf("vivo Y300+"), "15.0.10.1.W10.V000L1"),
        FallbackModel("PD2452", "Y", listOf("vivo Y300 GT"), "15.0.8.7.W10.V000L1"),
        FallbackModel("DPD2437", "其他", listOf("vivo Pad5"), "15.0.10.20.W10.V000L1"),
        FallbackModel("DPD2345E", "其他", listOf("vivo Pad5e"), "14.0.11.12.W10.V000L1"),
        FallbackModel("PD2352G", "iQOO", listOf("iQOO Z9 Turbo 长续航版"), "14.0.17.11.W10.V000L1"),
    )

    /** iQOO 机型。与 vivo 是不同的 model 体系（`PD` / `DPD` 前缀但号段不同）。
     *
     *  2026-10-04 重建：opusrom 的 46 款（合并成 38 条）+ 29 条新增，
     *  新增里包括源站一直没有的 **iQOO 15（PD2505 / V2505A）** 和
     *  **iQOO 16（PD2606 / V2606A）** —— 这两台的对外型号与 PD 号由
     *  vivo 官网参数页 + 工信部入网许可 + GSMchoice 三方交叉确认，
     *  版本串形如 `16.0.10.12.W10.V000L1`，可直接喂官方 OTA 接口。 */
    val iqoo: List<FallbackModel> = listOf(
        FallbackModel("PD2453", "iQOO", listOf("iQOO Z10 Turbo Pro"), "15.0.5.10.W10.V000L1"),
        FallbackModel("PD2452D", "iQOO", listOf("iQOO Z10 Turbo"), "15.0.8.7.W10.V000L1"),
        FallbackModel("PD2445D", "iQOO", listOf("iQOO Z10x"), "15.0.10.1.W10.V000L1"),
        FallbackModel("PD2426", "iQOO", listOf("iQOO Neo10 Pro"), "15.0.10.6.W10.V000L1"),
        FallbackModel("PD2425", "iQOO", listOf("iQOO Neo10"), "15.0.10.11.W10.V000L1"),
        FallbackModel("PD2417", "iQOO", listOf("iQOO Z9 Turbo+"), "14.0.10.0.W10.V000L1"),
        FallbackModel("PD2408", "iQOO", listOf("iQOO 13"), "15.0.14.2.W10.V000L1"),
        FallbackModel("PD2403", "iQOO", listOf("iQOO Neo9S Pro+"), "14.0.10.6.W10.V000L1"),
        FallbackModel("PD2361", "iQOO", listOf("iQOO Z9"), "14.0.10.0.W10.V000L1"),
        FallbackModel("PD2353", "iQOO", listOf("iQOO Z9x"), "14.0.10.1.W10.V000L1"),
        FallbackModel("PD2352", "iQOO", listOf("iQOO Z9 Turbo", "iQOO Z9 Turbo 长续航版"), "14.0.17.11.W10.V000L1"),
        FallbackModel("DPD2345", "iQOO", listOf("iQOO Pad2"), "14.0.11.12.W10.V000L1"),
        FallbackModel("PD2339", "iQOO", listOf("iQOO Neo9 Pro", "iQOO Neo9S Pro"), "14.0.13.3.W10.V000L1"),
        FallbackModel("PD2338", "iQOO", listOf("iQOO Neo9"), "14.0.11.6.W10.V000L1"),
        FallbackModel("DPD2329", "iQOO", listOf("iQOO Pad2 Pro"), "14.0.10.1.W10.V000L1"),
        FallbackModel("PD2314D", "iQOO", listOf("iQOO Z8"), "13.0.10.0.W10.V000L1"),
        FallbackModel("PD2312E", "iQOO", listOf("iQOO Z8x"), "13.0.10.0.W10.V000L1"),
        FallbackModel("PD2307", "iQOO", listOf("iQOO 12", "iQOO 12 Pro"), "14.0.15.15.W10.V000L1"),
        FallbackModel("PD2304", "iQOO", listOf("iQOO 11S"), "13.0.10.0.W10.V000L1"),
        FallbackModel("PD2302", "iQOO", listOf("iQOO Neo8 Pro"), "13.0.10.12.W10.V000L1"),
        FallbackModel("PD2301", "iQOO", listOf("iQOO Neo8"), "13.0.6.18.W10.V000L1"),
        FallbackModel("PD2272", "iQOO", listOf("iQOO Z7x", "iQOO Z7x (m)"), "13.0.11.0.W10.V000L1"),
        FallbackModel("PD2270", "iQOO", listOf("iQOO Z7"), "13.0.10.11.W10.V000L1"),
        FallbackModel("PD2254", "iQOO", listOf("iQOO 11 Pro"), "13.0.10.2.W10.V000L1"),
        FallbackModel("PD2243", "iQOO", listOf("iQOO 11"), "13.0.11.10.W10.V000L1"),
        FallbackModel("PD2238", "iQOO", listOf("iQOO Neo7 SE"), "13.0.11.2.W10.V000L1"),
        FallbackModel("PD2232", "iQOO", listOf("iQOO Neo7 竞速版"), "13.0.10.13.W10.V000L1"),
        FallbackModel("PD2231", "iQOO", listOf("iQOO Neo7"), "13.0.10.1.W10.V000L1"),
        FallbackModel("PD2230E", "iQOO", listOf("iQOO Z6e", "iQOO Z7i"), "13.0.12.5.W10.V000L1"),
        FallbackModel("DPD2221", "iQOO", listOf("iQOO Pad"), "13.0.11.11.W10.V000L1"),
        FallbackModel("PD2220", "iQOO", listOf("iQOO Z6"), "12.0.6.10.W10.V000L1"),
        FallbackModel("PD2217", "iQOO", listOf("iQOO 10", "iQOO 10 Pro"), "12.1.14.5.W10.V000L1"),
        FallbackModel("PD2199G", "iQOO", listOf("iQOO Neo6 SE"), "12.0.11.6.W10.V000L1"),
        FallbackModel("PD2196", "iQOO", listOf("iQOO Neo6"), "12.0.10.4.W10.V000L1"),
        FallbackModel("PD2188", "iQOO", listOf("iQOO Z5 6000mAh版"), "12.0.10.1.W10.V000L1"),
        FallbackModel("PD2180D", "iQOO", listOf("iQOO U5x"), "11.0.10.0.W10.V000L1"),
        FallbackModel("PD2171", "iQOO", listOf("iQOO 9", "iQOO 9 Pro"), "12.0.17.4.W10.V000L1"),
        FallbackModel("PD2165", "iQOO", listOf("iQOO U5", "iQOO U5 4GB+128GB版"), "12.0.14.10.W10.V000L1"),
        FallbackModel("PD2505", "iQOO", listOf("iQOO 15"), "16.0.10.12.W10.V000L1"),
        FallbackModel("PD2606", "iQOO", listOf("iQOO 16"), "17.0.10.15.W10.V000L1"),
        FallbackModel("PD2438F", "iQOO", listOf("iQOO Z10R 5G"), "15.0.4.8.W30.V000L1"),
        FallbackModel("PD2443F", "iQOO", listOf("iQOO Z10 Lite 5G"), "15.0.4.8.W30.V000L1"),
        FallbackModel("PD2456F", "iQOO", listOf("iQOO Z10 5G"), "15.0.6.2.W30.V000L1"),
        FallbackModel("PD2463", "iQOO", listOf("iQOO Neo10 Pro+"), "15.0.10.11.W10.V000L1"),
        FallbackModel("PD2507", "iQOO", listOf("iQOO Z10 Turbo+", "iQOO Z10 Turbo 长续航版"), "15.0.8.7.W10.V000L1"),
        FallbackModel("PD2520", "iQOO", listOf("iQOO Neo11"), "16.0.10.8.W10.V000L1"),
        FallbackModel("PD2532F", "iQOO", listOf("iQOO Z11X 5G"), "16.0.10.12.W30.V000L1"),
        FallbackModel("PD2542F", "iQOO", listOf("iQOO Z11 Lite 5G"), "16.0.10.9.W30.V000L1"),
        FallbackModel("PD2553F", "iQOO", listOf("iQOO Z11 5G"), "16.0.10.15.W30.V000L1"),
        FallbackModel("PD2536F", "iQOO", listOf("iQOO 15R"), "16.0.10.10.W30.V000L1"),
        FallbackModel("PD2406F", "iQOO", listOf("iQOO Z9S 5G"), "15.0.4.8.W30.V000L1"),
        FallbackModel("PD2404F", "iQOO", listOf("iQOO Z9s Pro 5G"), "15.0.4.6.W30.V000L1"),
        FallbackModel("PD2346F", "iQOO", listOf("iQOO Z9 5G"), "15.0.4.4.W30.V000L1"),
        FallbackModel("PD2225F", "iQOO", listOf("iQOO Z6X"), "12.0.10.8.W30.V000L1"),
        FallbackModel("PD2257F", "iQOO", listOf("iQOO Z7 5G"), "13.0.10.9.W30.V000L1"),
        FallbackModel("DPD2437", "iQOO", listOf("iQOO Pad5"), "15.0.10.20.W10.V000L1"),
        FallbackModel("DPD2437P", "iQOO", listOf("iQOO Pad5 Pro"), "15.0.12.18.W10.V000L1"),
        FallbackModel("PD2172", "iQOO", listOf("iQOO 9 Pro"), "12.0.17.4.W10.V000L1"),
        FallbackModel("PD2218", "iQOO", listOf("iQOO 10 Pro"), "12.1.14.5.W10.V000L1"),
        FallbackModel("PD2329", "iQOO", listOf("iQOO 12 Pro"), "14.0.15.15.W10.V000L1"),
        FallbackModel("PD2232B", "iQOO", listOf("iQOO Neo7 Pro"), "13.0.10.13.W10.V000L1"),
        FallbackModel("PD2199B", "iQOO", listOf("iQOO Neo6"), "12.0.11.6.W10.V000L1"),
        FallbackModel("PD2073", "iQOO", listOf("iQOO Z3"), "11.0.10.16.W10.V000L1"),
        FallbackModel("PD2131", "iQOO", listOf("iQOO Z5x"), "12.0.10.6.W10.V000L1"),
        FallbackModel("PD2197", "iQOO", listOf("iQOO U5e"), "12.0.10.9.W10.V000L1"),
        FallbackModel("PD2143", "iQOO", listOf("iQOO U3x 标准版"), "13.0.10.8.W10.V000L1"),
        FallbackModel("PD2106", "iQOO", listOf("iQOO U3x"), "12.0.10.10.W10.V000L1"),
    )

    // ------------------------------------------------------------- ColorOS 系


    /**
     * OPPO / 一加 / 真我。
     *
     * ColorOS 的 `model` 形如`PHQ110`（OPPO）、`RMX3751`（真我），后面跟完整版本号。
     *
     * 这里只放**能从 opusrom 实际数据里查到**的条目，不凭印象填 ——
     * model 填错了官方接口会直接返回空，白白浪费一次请求。
     * 机型不全没关系：正常路径是走 opusrom 在线清单（571 机型），
     * 这张表只是opusrom 挂了时的兜底。
     */
    val colorOs: List<FallbackModel> = listOf(
        FallbackModel("PGP110", "ColorOS", listOf("一加 Ace Pro"), "PGP110_15.0.0.1602(CN01)"),
        FallbackModel("PGX110", "ColorOS", listOf("OPPO Reno 9 Pro"), "PGX110_15.0.0.1351(CN01)"),
        FallbackModel("PGZ110", "ColorOS", listOf("一加 Ace 竞速版"), "PGZ110_12.1"),
        FallbackModel("PHF110", "ColorOS", listOf("OPPO K11x"), "PHF110_13.1.0.222(CN01)"),
        FallbackModel("PHM110", "ColorOS", listOf("OPPO Reno 9"), "PHM110_13.1.0.185(CN01)"),
        FallbackModel("PHN110", "ColorOS", listOf("OPPO 其他型号"), "PHN110_15.0.0.400(CN01)"),
        FallbackModel("PHQ110", "ColorOS", listOf("OPPO A1 Pro"), "PHQ110_14.0.0.700(CN01)"),
        FallbackModel("PHS110", "ColorOS", listOf("OPPO A1 5G"), "PHS110_13.1.0.192(CN01)"),
        FallbackModel("PHV110", "ColorOS", listOf("OPPO Reno 10 Pro"), "PHV110_13.1.1.422(CN01)"),
        FallbackModel("PHW110", "ColorOS", listOf("OPPO Reno 10"), "PHW110_13.1.1.407(CN01)"),
        FallbackModel("PJC110", "ColorOS", listOf("OPPO K11"), "PJC110_15.0.0.1602(CN01)"),
        FallbackModel("PJG110", "ColorOS", listOf("OPPO A2 Pro"), "PJG110_14.0.0.200(CN01)"),
        FallbackModel("PJS110", "ColorOS", listOf("OPPO A2x"), "PJS110_14.0.0.800(CN01)"),
        FallbackModel("PKD110", "ColorOS", listOf("OPPO A3 活力版"), "PKD110_15.0.0.1600(CN01)"),
        FallbackModel("PKD130", "ColorOS", listOf("OPPO A3x"), "PKD130_15.0.0.1600(CN01)"),
        FallbackModel("PKL110", "ColorOS", listOf("OPPO A3i"), "PKL110_15.0.0.1600(CN01)"),
    )

    // ------------------------------------------------------------- 查询

    /** 按品牌 key 取兜底机型。品牌 key 见 [BrandCatalog]。 */
    fun of(brandKey: String): List<FallbackModel> = when (brandKey.lowercase()) {
        "vivo" -> vivo
        "iqoo" -> iqoo
        "oppo", "oneplus", "realme" -> colorOs
        else -> emptyList()
    }

    /**
     * 兜底表里的机型转成和 opusrom 一样的结构，
     * 这样上层界面不用关心数据是哪来的。
     *
     * 版本列表里放一个占位版本：直链要现调官方接口取，
     * 具体能升到哪一版由接口说了算，静态表不猜。
     */
    fun asOpusDevices(brandKey: String, brandName: String): List<OpusDeviceFull> {
        val brand = brandKey.lowercase()
        val family = BrandCatalog.byKey(brandKey)?.family
        // 一个 FallbackModel 可能对应多个机型名（共用同一套固件），
        // 但**必须拆成多个独立条目** —— 拆开用户才能在列表里单独搜到、
        // 单独点进去；合并成 "X200 / X200 Pro mini / X200s" 一条用户就没法选了。
        // 共用固件这件事由 [model] 体现，不影响列表展示。
        return of(brandKey).flatMap { fm ->
            // 源站（opusrom）的 OriginOS 版本串是 `PD2217_MA_12.1.14.5.W10.V000L1`
            // 这种整串形式，`version` 字段直接存它（不是裸版本号）。
            // 这里必须照着拼，否则：
            //   1) 上层按 PD 号判重会失效（正则从 romName 里抠 PD，裸版本号里没有 PD）
            //   2) 解析接口的 key 拼错，必然 502
            // ColorOS 系格式不同（`PJZ110_...`），保持原样不套这套规则。
            val fullVersion = if (family == BrandFamily.ORIGIN_OS &&
                !fm.sampleVersion.startsWith(fm.model)
            ) {
                "${fm.model}_MA_${fm.sampleVersion}"
            } else {
                fm.sampleVersion
            }
            fm.names.map { name ->
                val version = OpusVersion(
                    version = fullVersion,
                    romName = fullVersion,
                    osFamily = when (family) {
                        BrandFamily.ORIGIN_OS -> "标准(W10)"
                        BrandFamily.COLOR_OS -> fm.sampleVersion.substringBefore("_")
                        else -> ""
                    },
                    flashType = "全量包",
                    size = "",
                    releaseDate = "",
                    region = "国行",
                    md5 = "",
                    needsResolve = true,
                    apiBrand = brand,
                    apiDevice = name,
                    apiMajor = if (family == BrandFamily.ORIGIN_OS) "标准(W10)" else "",
                    apiFlash = "线刷包",
                    // ⚠️ 源站 iQOO 记录的 plusModel 是**空的**（实测 47/47 条全空），
                    // 照抄源站行为置空。之前这里填了 PD 号，反而和源站对不上。
                    oplusModel = "",
                )
                OpusDeviceFull(
                    brandKey = brand,
                    brand = brandName,
                    series = fm.series,
                    name = name,
                    codename = "—",
                    type = "官方固件",
                    region = "国行",
                    fileType = "全量包",
                    versionCount = 1,
                    description = "内置机型（${fm.model}）。版本与直链向官方 OTA 接口实时查询，" +
                        "该接口只提供当前可推送的最新版本。",
                    versions = listOf(version),
                )
            }
        }
    }
}
