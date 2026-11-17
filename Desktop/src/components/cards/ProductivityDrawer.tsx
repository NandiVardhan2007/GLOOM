import React, { useState, useEffect, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { writeText } from "@tauri-apps/plugin-clipboard-manager";
import {
	Inbox,
	Clipboard,
	FileText,
	Copy,
	Check,
	Trash2,
	Type,
	X
} from "lucide-react";
import { ShelfTypeIcon } from "../ShelfTypeIcon";
import { playTactileSound } from "../../utils/audioFeedback";
import { formatRelativeTime, countWordsAndChars } from "./productivityUtils";
import "./ProductivityDrawer.css";

export interface ShelfItem {
	id: string;
	name: string;
	type: "file" | "text";
	path?: string;
	text?: string;
	timestamp: number;
}

export interface ShelfPreview {
	thumb?: string | null;
	text?: string | null;
	folder?: boolean;
}

export interface ClipboardItem {
	id: string;
	text: string;
	timestamp: number;
	char_count: number;
}

export interface ProductivityDrawerProps {
	shelfItems: ShelfItem[];
	shelfThumbs: Record<string, ShelfPreview>;
	shelfNotice?: string | null;
	shelfOverflowing?: boolean;
	shelfGridRef?: React.RefObject<HTMLDivElement | null>;
	measureShelfOverflow?: () => void;
	shelfNewIdsRef?: React.MutableRefObject<Set<string>>;
	handleShelfPointerDown?: (e: React.PointerEvent<HTMLDivElement>, item: ShelfItem) => void;
	handleShelfPointerMove?: (e: React.PointerEvent<HTMLDivElement>, item: ShelfItem) => void;
	handleShelfTileClick?: (e: React.MouseEvent<HTMLDivElement>, item: ShelfItem) => void;
	removeShelfItem: (id: string) => void;
	handleShelfWheel?: (e: React.WheelEvent<HTMLDivElement>) => void;
}

type ProductivityTab = "files" | "clipboard" | "scratchpad";

export function ProductivityDrawer({
	shelfItems,
	shelfThumbs,
	shelfNotice,
	shelfOverflowing,
	shelfGridRef,
	measureShelfOverflow,
	shelfNewIdsRef,
	handleShelfPointerDown,
	handleShelfPointerMove,
	handleShelfTileClick,
	removeShelfItem,
	handleShelfWheel
}: ProductivityDrawerProps) {
	const [activeTab, setActiveTab] = useState<ProductivityTab>("files");
	const [clipboardHistory, setClipboardHistory] = useState<ClipboardItem[]>([]);
	const [copiedClipId, setCopiedId] = useState<string | null>(null);
	const [scratchpadText, setScratchpadText] = useState<string>(() => {
		try {
			return localStorage.getItem("gloom-scratchpad-content") || "";
		} catch {
			return "";
		}
	});
	const [scratchpadCopied, setScratchpadCopied] = useState(false);
	const isFocusedRef = useRef(false);

	// Load clipboard items on mount & listen for real-time Win32 clipboard updates
	useEffect(() => {
		invoke<ClipboardItem[]>("get_clipboard_history")
			.then((items) => {
				if (Array.isArray(items)) setClipboardHistory(items);
			})
			.catch(() => {});

		const unlistenPromise = listen<ClipboardItem[]>("clipboard-history-updated", (event) => {
			if (Array.isArray(event.payload)) {
				setClipboardHistory(event.payload);
			}
		});

		return () => {
			unlistenPromise.then((unlisten) => unlisten());
		};
	}, []);

	// Save scratchpad content
	useEffect(() => {
		try {
			localStorage.setItem("gloom-scratchpad-content", scratchpadText);
		} catch {
			/* storage full or unavailable */
		}
	}, [scratchpadText]);

	// Clean up Win32 keyboard focus on unmount
	useEffect(() => {
		return () => {
			if (isFocusedRef.current) {
				invoke("request_notch_keyboard_focus", { focus: false }).catch(() => {});
				isFocusedRef.current = false;
			}
		};
	}, []);

	const handleTabChange = (tab: ProductivityTab) => {
		if (tab !== activeTab) {
			playTactileSound("switch");
			setActiveTab(tab);
		}
	};

	const copyClipItem = async (e: React.MouseEvent, item: ClipboardItem) => {
		e.stopPropagation();
		try {
			await writeText(item.text);
			playTactileSound("snap");
			setCopiedId(item.id);
			setTimeout(() => {
				setCopiedId((curr) => (curr === item.id ? null : curr));
			}, 1200);
		} catch {
			/* failed to copy */
		}
	};

	const clearAllClips = async (e: React.MouseEvent) => {
		e.stopPropagation();
		try {
			await invoke("clear_clipboard_history");
			setClipboardHistory([]);
			playTactileSound("pop");
		} catch {
			/* clear error */
		}
	};

	const handleScratchpadFocus = () => {
		isFocusedRef.current = true;
		invoke("request_notch_keyboard_focus", { focus: true }).catch(() => {});
	};

	const handleScratchpadBlur = () => {
		isFocusedRef.current = false;
		invoke("request_notch_keyboard_focus", { focus: false }).catch(() => {});
	};

	const copyScratchpad = async () => {
		if (!scratchpadText) return;
		try {
			await writeText(scratchpadText);
			playTactileSound("snap");
			setScratchpadCopied(true);
			setTimeout(() => setScratchpadCopied(false), 1200);
		} catch {
			/* failed to copy */
		}
	};

	const clearScratchpad = () => {
		if (!scratchpadText) return;
		setScratchpadText("");
		playTactileSound("pop");
	};

	const counts = countWordsAndChars(scratchpadText);

	return (
		<motion.div
			className="productivity-drawer-container"
			onClick={(e) => e.stopPropagation()}
			initial={{ opacity: 0 }}
			animate={{ opacity: 1 }}
			exit={{ opacity: 0, filter: "blur(4px)", transition: { duration: 0.1 } }}
			transition={{ type: "spring", stiffness: 400, damping: 30 }}
		>
			{/* Segmented Apple-Style Tab Header */}
			<div className="productivity-nav-header">
				<div className="productivity-segmented-control">
					<button
						type="button"
						className={`productivity-segment-btn ${activeTab === "files" ? "active" : ""}`}
						onClick={() => handleTabChange("files")}
					>
						<Inbox size={12} strokeWidth={2.2} />
						<span>Files</span>
						{shelfItems.length > 0 && <span className="tab-pill-badge">{shelfItems.length}</span>}
					</button>

					<button
						type="button"
						className={`productivity-segment-btn ${activeTab === "clipboard" ? "active" : ""}`}
						onClick={() => handleTabChange("clipboard")}
					>
						<Clipboard size={12} strokeWidth={2.2} />
						<span>Clips</span>
						{clipboardHistory.length > 0 && (
							<span className="tab-pill-badge">{clipboardHistory.length}</span>
						)}
					</button>

					<button
						type="button"
						className={`productivity-segment-btn ${activeTab === "scratchpad" ? "active" : ""}`}
						onClick={() => handleTabChange("scratchpad")}
					>
						<FileText size={12} strokeWidth={2.2} />
						<span>Scratchpad</span>
						{scratchpadText.trim().length > 0 && <span className="tab-dot-badge" />}
					</button>
				</div>
			</div>

			{/* Tab Body Panes */}
			<div className="productivity-body-pane">
				<AnimatePresence mode="wait">
					{/* TAB 1: FILES (SHELF) */}
					{activeTab === "files" && (
						<motion.div
							key="files-tab"
							className="productivity-tab-page files-page"
							onWheel={handleShelfWheel}
							initial={{ opacity: 0, y: 4 }}
							animate={{ opacity: 1, y: 0 }}
							exit={{ opacity: 0, y: -4, transition: { duration: 0.1 } }}
							transition={{ duration: 0.15 }}
						>
							{shelfNotice && <div className="shelf-notice">{shelfNotice}</div>}

							{shelfItems.length === 0 ? (
								<div className="shelf-empty">
									<Inbox size={18} strokeWidth={1.8} className="shelf-empty-icon" />
									<span>Drop files or selected text here to stash</span>
								</div>
							) : (
								<div
									className={`shelf-items-grid ${shelfOverflowing ? "overflowing" : ""}`}
									ref={shelfGridRef as React.RefObject<HTMLDivElement>}
								>
									<AnimatePresence mode="popLayout" onExitComplete={measureShelfOverflow}>
										{shelfItems.map((item) => {
											const preview = item.path ? shelfThumbs[item.path] : undefined;
											const thumb = preview?.thumb;
											return (
												<motion.div
													key={item.id}
													layout
													initial={
														shelfNewIdsRef?.current.has(item.id)
															? { opacity: 0, scale: 0.82 }
															: false
													}
													animate={{ opacity: 1, scale: 1 }}
													exit={{
														opacity: 0,
														scale: 0.82,
														transition: { duration: 0.16, ease: "easeOut" }
													}}
													transition={{
														type: "spring",
														stiffness: 520,
														damping: 38,
														mass: 0.7
													}}
													className={`shelf-tile ${item.type === "text" || (!thumb && preview?.text) ? "is-text" : ""}`}
													title={item.type === "text" ? item.text?.slice(0, 1000) : item.path}
													onPointerDown={(e) => handleShelfPointerDown?.(e, item)}
													onPointerMove={(e) => handleShelfPointerMove?.(e, item)}
													onClick={(e) => handleShelfTileClick?.(e, item)}
												>
													<div className="shelf-tile-preview">
														{item.type === "text" ? (
															<div className="shelf-tile-text">
																<Type
																	size={11}
																	strokeWidth={2}
																	className="shelf-tile-text-icon"
																/>
																<span className="shelf-tile-text-body">{item.text}</span>
															</div>
														) : !thumb && preview?.text ? (
															<div className="shelf-tile-text">
																<ShelfTypeIcon
																	name={item.name}
																	folder={false}
																	size={11}
																	className="shelf-tile-text-icon"
																/>
																<span className="shelf-tile-text-body">{preview.text}</span>
															</div>
														) : (
															<>
																<div className="shelf-tile-placeholder">
																	{preview && !thumb ? (
																		<ShelfTypeIcon
																			name={item.name}
																			folder={!!preview?.folder}
																		/>
																	) : null}
																</div>
																{thumb ? (
																	<img src={thumb} alt="" draggable={false} />
																) : null}
															</>
														)}
													</div>
													<span className="shelf-tile-name">
														{item.type === "text"
															? `Text · ${item.text?.length ?? 0} chars`
															: item.name}
													</span>
													<button
														type="button"
														className="shelf-tile-remove"
														title="Remove from shelf"
														onClick={(e) => {
															e.stopPropagation();
															removeShelfItem(item.id);
														}}
													>
														<X size={11} strokeWidth={2.5} />
													</button>
												</motion.div>
											);
										})}
									</AnimatePresence>
								</div>
							)}
						</motion.div>
					)}

					{/* TAB 2: CLIPBOARD HISTORY */}
					{activeTab === "clipboard" && (
						<motion.div
							key="clipboard-tab"
							className="productivity-tab-page clipboard-page"
							initial={{ opacity: 0, y: 4 }}
							animate={{ opacity: 1, y: 0 }}
							exit={{ opacity: 0, y: -4, transition: { duration: 0.1 } }}
							transition={{ duration: 0.15 }}
						>
							<div className="clipboard-toolbar">
								<span className="clipboard-title">
									Recent Copies ({clipboardHistory.length})
								</span>
								{clipboardHistory.length > 0 && (
									<button
										type="button"
										className="clipboard-clear-btn"
										onClick={clearAllClips}
										title="Clear clipboard history"
									>
										<Trash2 size={11} strokeWidth={2} />
										<span>Clear</span>
									</button>
								)}
							</div>

							{clipboardHistory.length === 0 ? (
								<div className="shelf-empty">
									<Clipboard size={18} strokeWidth={1.8} className="shelf-empty-icon" />
									<span>No clipboard history yet. Copied text will appear here.</span>
								</div>
							) : (
								<div className="clipboard-items-list">
									{clipboardHistory.map((clip) => {
										const isCopied = copiedClipId === clip.id;
										return (
											<div
												key={clip.id}
												className="clipboard-card"
												onClick={(e) => copyClipItem(e, clip)}
												title="Click to copy back to clipboard"
											>
												<div className="clipboard-card-body">
													<p className="clipboard-card-text">{clip.text}</p>
													<div className="clipboard-card-meta">
														<span>{formatRelativeTime(clip.timestamp)}</span>
														<span>·</span>
														<span>{clip.char_count} chars</span>
													</div>
												</div>
												<button
													type="button"
													className={`clipboard-card-copy-btn ${isCopied ? "copied" : ""}`}
													onClick={(e) => copyClipItem(e, clip)}
													title="Copy to clipboard"
												>
													{isCopied ? (
														<Check size={12} strokeWidth={2.4} />
													) : (
														<Copy size={12} strokeWidth={2} />
													)}
												</button>
											</div>
										);
									})}
								</div>
							)}
						</motion.div>
					)}

					{/* TAB 3: QUICK SCRATCHPAD */}
					{activeTab === "scratchpad" && (
						<motion.div
							key="scratchpad-tab"
							className="productivity-tab-page scratchpad-page"
							initial={{ opacity: 0, y: 4 }}
							animate={{ opacity: 1, y: 0 }}
							exit={{ opacity: 0, y: -4, transition: { duration: 0.1 } }}
							transition={{ duration: 0.15 }}
						>
							<div className="scratchpad-toolbar">
								<div className="scratchpad-counts">
									<span>{counts.words} words</span>
									<span>·</span>
									<span>{counts.chars} chars</span>
								</div>
								<div className="scratchpad-actions">
									{scratchpadText.length > 0 && (
										<>
											<button
												type="button"
												className="scratchpad-action-btn"
												onClick={copyScratchpad}
												title="Copy entire note"
											>
												{scratchpadCopied ? (
													<Check size={11} strokeWidth={2.4} />
												) : (
													<Copy size={11} strokeWidth={2} />
												)}
												<span>{scratchpadCopied ? "Copied" : "Copy"}</span>
											</button>
											<button
												type="button"
												className="scratchpad-action-btn clear"
												onClick={clearScratchpad}
												title="Clear note"
											>
												<Trash2 size={11} strokeWidth={2} />
												<span>Clear</span>
											</button>
										</>
									)}
								</div>
							</div>

							<div className="scratchpad-textarea-wrapper">
								<textarea
									className="scratchpad-textarea"
									placeholder="Type quick thoughts, phone numbers, or code snippets here... Automatically saved."
									value={scratchpadText}
									onChange={(e) => setScratchpadText(e.target.value)}
									onFocus={handleScratchpadFocus}
									onBlur={handleScratchpadBlur}
									spellCheck={false}
								/>
							</div>
						</motion.div>
					)}
				</AnimatePresence>
			</div>
		</motion.div>
	);
}
